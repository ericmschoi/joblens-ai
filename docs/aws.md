# Deploying JobLens to AWS

**Nothing has been deployed.** The infrastructure in `infrastructure/` is written, tested and
synthesized locally. No AWS account has been touched, no resource created, no image pushed. This
document is what a deployment would cost and do, so that decision can be made with the numbers in
front of you.

## The shape

```
                      ┌──────────────────────────────┐
   browser ─── HTTPS ─│  CloudFront distribution     │
                      │                              │
                      │  /*      → S3 (private, OAC) │  the built SPA
                      │  /api/*  → Lambda URL (IAM)  │  the API
                      └──────────────────────────────┘
```

One origin from the browser's point of view, so a deployed environment is same-origin and CORS never
applies. The S3 bucket blocks all public access and is reachable only through the distribution's
origin access control. The function URL is `AWS_IAM`-authenticated, so it cannot be called directly
either — only CloudFront can invoke it.

The API is the same Spring Boot application, in the same container, with the AWS Lambda Web Adapter
added as an extension (`backend/Dockerfile.lambda`). The application does not know it is in Lambda.

## Why this and not ECS Fargate behind a load balancer

An Application Load Balancer costs about **$16–18 a month before it serves a single request**, and
two small Fargate tasks add roughly **$18 a month** whether or not anyone uses them. That is
~$35/month standing charge for a personal project that is idle most of the time.

Every component chosen here is either free at rest or billed per request:

| Resource | Idle cost | Note |
|---|---|---|
| Lambda (2 GB, arm64) | **$0** | Billed per millisecond of execution. Nothing runs between requests. |
| CloudFront | **$0** | 1 TB out and 10M requests a month are free under the perpetual free tier. |
| S3 (SPA assets, ~1 MB) | **~$0.00003** | Storage is $0.023/GB-month. |
| ECR (one ~250 MB image) | **~$0.025** | $0.10/GB-month, ten-image lifecycle rule. |
| CloudWatch Logs | **~$0** | 5 GB ingest free; 14-day retention. |
| AWS Budgets | **$0** | First two budgets are free. |
| SNS (budget email) | **$0** | First 1,000 email notifications free. |
| **Total, idle** | **≈ $0.03 / month** | |

There is deliberately **no NAT gateway** (~$32/month), no VPC, no load balancer, no database, no
ElastiCache and no WAF. `infrastructure/test/stacks.test.ts` asserts that none of them appear in the
synthesized template, so a future change that adds one has to argue for it in a test first.

### What it costs when used

Rough figures for the Canada (Central) region, and generous about how long an analysis takes:

- One analysis ≈ 3 seconds at 2 GB = 6 GB-seconds ≈ **$0.0001**
- A cold start adds ~8–12 seconds of billed time on the first request after idleness ≈ **$0.0004**
- 1,000 analyses a month ≈ **$0.10 in Lambda**, plus a few cents of CloudFront transfer

So a realistic month with genuine use is still **well under a dollar**. The free tiers cover most of
it. The largest plausible surprise is CloudFront egress if something hotlinks the assets, which is
what the budget alert exists to catch.

### The cost of this choice

Cold starts. A JVM in Lambda takes roughly **8–12 seconds** to answer the first request after a
period of idleness; subsequent requests are fast. `SPRING_MAIN_LAZY_INITIALIZATION` and
`-XX:TieredStopAtLevel=1` reduce it, and the Lambda Web Adapter's readiness check means a request is
never routed into a half-started application — it waits.

Provisioned concurrency would remove the cold start and would cost about **$16 a month per
concurrent execution**, which is the same order as the load balancer this design avoids. That is not
a good trade for a portfolio project, so it is not configured. If it ever matters, it is one property
on the function.

## What deploying actually creates

Deploying `JoblensRegistry` creates:

- one ECR repository, `joblens-backend`, empty, immutable tags, scan on push, ten-image lifecycle
  rule

Deploying `JoblensApplication` creates:

- one Lambda function (2 GB, arm64, 60 s timeout, no VPC) and its function URL
- one CloudWatch log group, 14-day retention
- one private S3 bucket and its origin access control policy, plus the SPA objects
- one CloudFront distribution (PriceClass_100) and one response-headers policy
- one AWS Budget, one SNS topic and one email subscription
- the IAM roles those need, and the CDK bootstrap stack if the account has never used CDK

Region: **ca-central-1** unless `CDK_DEFAULT_REGION` says otherwise.

## Doing it

Everything below is behind the approval gate: each step either creates a resource, pushes an image
or spends money.

```bash
# 0. once per account and region — creates the CDK bootstrap stack (an S3 bucket, an ECR repo,
#    a few IAM roles; a few cents a month)
cd infrastructure && npx cdk bootstrap aws://<account-id>/ca-central-1

# 1. the registry — an empty repository, free until an image is pushed
npx cdk deploy JoblensRegistry

# 2. build and push the image (this is a billable action: ECR storage and transfer)
aws ecr get-login-password --region ca-central-1 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ca-central-1.amazonaws.com
docker buildx build --platform linux/arm64 -f backend/Dockerfile.lambda \
  -t <account-id>.dkr.ecr.ca-central-1.amazonaws.com/joblens-backend:sha-$(git rev-parse --short HEAD) \
  --push backend

# 3. the application
cd frontend && npm ci && npm run build && cd ../infrastructure
npx cdk deploy JoblensApplication \
  -c imageTag=sha-$(git rev-parse --short HEAD) \
  -c budgetAlertEmail=you@example.com
```

Or run the `Deploy` workflow from the Actions tab, which does the same thing with the run recorded.
It is manual-dispatch only, requires typing `deploy` into a confirmation input, targets a
`production` environment that can require a reviewer, and needs an `AWS_DEPLOY_ROLE_ARN` variable
that does not exist yet. **No merge to any branch can trigger it.**

## Tearing it down

```bash
cd infrastructure
npx cdk destroy JoblensApplication
npx cdk destroy JoblensRegistry
```

Everything in both stacks has `RemovalPolicy.DESTROY`: the bucket empties itself
(`autoDeleteObjects`), the ECR repository empties itself (`emptyOnDelete`), and the log group is
deleted rather than retained. After both stacks are destroyed, the only thing left in the account is
the CDK bootstrap stack, which costs a few cents a month and can be deleted from the CloudFormation
console once no other CDK app in that account needs it.

To check nothing was missed:

```bash
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE
aws ecr describe-repositories
aws s3 ls
aws cloudfront list-distributions
aws logs describe-log-groups --log-group-name-prefix /aws/lambda
```

## Verified locally

- `npm run typecheck` in `infrastructure/` — clean
- `npm test` — **13 tests**, asserting no idle-billed resource, no VPC, no public function URL, no
  cached API responses, a private bucket, bounded log retention, a budget alert, destroy-on-delete
  everywhere, and that no secret string appears in the synthesized template
- `npx cdk synth` — both stacks synthesize with no errors and no warnings, with no AWS credentials
  and no context lookups
- `docker build -f backend/Dockerfile.lambda` — builds; the container answers
  `{"status":"UP"}` on `/actuator/health`, and the adapter is present at
  `/opt/extensions/lambda-adapter`
