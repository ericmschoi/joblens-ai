import * as path from 'node:path';
import * as fs from 'node:fs';

import {
  CfnOutput,
  Duration,
  RemovalPolicy,
  Stack,
  StackProps,
} from 'aws-cdk-lib';
import * as budgets from 'aws-cdk-lib/aws-budgets';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';

export interface JoblensApplicationStackProps extends StackProps {
  /** Immutable tag of the backend image already pushed to the registry stack's repository. */
  readonly imageTag: string;
  /** Where the built SPA lives. Built by `npm run build` in `frontend/` before synthesis. */
  readonly frontendDistPath: string;
  /** Address that receives the budget alert. Omitted means no alert is created. */
  readonly budgetAlertEmail?: string;
  /** Monthly spend, in USD, at which the alert fires. */
  readonly monthlyBudgetUsd: number;
}

/**
 * The whole application: a static SPA, an API, and one distribution in front of both.
 *
 * <p>The shape is chosen for what this product actually is — no database, nothing persisted,
 * bursty personal traffic — and for what it costs when nobody is using it. Every component here
 * either scales to zero or is charged by the byte, so an idle month is cents rather than the tens
 * of dollars a load balancer and a permanently running container would cost.
 *
 * <p>The API is reached through the same distribution as the SPA, so the browser is same-origin and
 * CORS never enters a deployed environment. The function URL is not public: only CloudFront can
 * invoke it.
 */
export class JoblensApplicationStack extends Stack {
  constructor(scope: Construct, id: string, props: JoblensApplicationStackProps) {
    super(scope, id, props);

    if (!fs.existsSync(path.join(props.frontendDistPath, 'index.html'))) {
      throw new Error(
        `No built frontend at ${props.frontendDistPath}. Run "npm run build" in frontend/ first.`,
      );
    }

    // --- the API ------------------------------------------------------------------------------

    const repository = ecr.Repository.fromRepositoryName(
      this,
      'BackendRepository',
      'joblens-backend',
    );

    // An explicit log group, so retention and deletion are part of the stack rather than left to
    // a default that keeps data — and charges for it — forever.
    const backendLogs = new logs.LogGroup(this, 'BackendLogs', {
      retention: logs.RetentionDays.TWO_WEEKS,
      removalPolicy: RemovalPolicy.DESTROY,
    });

    const backend = new lambda.DockerImageFunction(this, 'Backend', {
      code: lambda.DockerImageCode.fromEcr(repository, { tagOrDigest: props.imageTag }),
      architecture: lambda.Architecture.ARM_64,
      // A JVM starts faster with more memory because CPU is allocated in proportion to it. 2 GB is
      // the point past which JobLens stops getting meaningfully quicker to start.
      memorySize: 2048,
      // Long enough for a cold start plus an analysis; short enough that a stuck request cannot
      // run up a bill.
      timeout: Duration.seconds(60),
      environment: {
        JOBLENS_ANALYSIS_PROVIDER: 'fake',
        // Same-origin through CloudFront, so no browser origin is ever allowed cross-origin.
        JOBLENS_CORS_ALLOWED_ORIGINS: '',
        SPRING_MAIN_LAZY_INITIALIZATION: 'true',
        AWS_LWA_PORT: '8080',
        AWS_LWA_READINESS_CHECK_PATH: '/actuator/health',
        AWS_LWA_INVOKE_MODE: 'buffered',
      },
      // Logs are the only thing this deployment stores, and they are charged by the gigabyte.
      logGroup: backendLogs,
      // No VPC: the backend fetches public job pages and nothing else, so a NAT gateway — the most
      // expensive idle resource in a typical AWS design at roughly $32 a month — is not needed.
      description: 'JobLens API. Stateless, persists nothing, holds no credentials.',
    });

    const backendUrl = backend.addFunctionUrl({
      authType: lambda.FunctionUrlAuthType.AWS_IAM,
      invokeMode: lambda.InvokeMode.BUFFERED,
    });

    // --- the SPA ------------------------------------------------------------------------------

    const siteBucket = new s3.Bucket(this, 'SiteBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      versioned: false,
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });

    const distribution = new cloudfront.Distribution(this, 'Distribution', {
      comment: 'JobLens AI',
      defaultRootObject: 'index.html',
      httpVersion: cloudfront.HttpVersion.HTTP2_AND_3,
      // North America and Europe only: the cheapest price class, and where this is used.
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
      // No minimumProtocolVersion here. Without a custom certificate CloudFront ignores it, and
      // this deployment uses the distribution's own domain. It belongs here the day a domain does.
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(siteBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD_OPTIONS,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        responseHeadersPolicy: this.siteHeadersPolicy(),
        compress: true,
      },
      additionalBehaviors: {
        // The API is dynamic, carries no-store, and must never be cached. Every header and the
        // request body are forwarded; the origin decides everything.
        '/api/*': {
          origin: origins.FunctionUrlOrigin.withOriginAccessControl(backendUrl),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          compress: true,
        },
      },
      errorResponses: [
        // A single-page app: an unknown path is a route, not a missing file. Only 403/404 from S3
        // are rewritten; an API failure keeps its own status and its own problem-detail body.
        { httpStatus: 403, responseHttpStatus: 200, responsePagePath: '/index.html' },
        { httpStatus: 404, responseHttpStatus: 200, responsePagePath: '/index.html' },
      ],
    });

    new s3deploy.BucketDeployment(this, 'SiteContents', {
      sources: [s3deploy.Source.asset(props.frontendDistPath)],
      destinationBucket: siteBucket,
      distribution,
      distributionPaths: ['/index.html'],
      prune: true,
      // The assets are a few hundred kilobytes; the smallest size the deployment helper offers is
      // ample and keeps its own run cheap.
      memoryLimit: 256,
    });

    // --- the bill -----------------------------------------------------------------------------

    if (props.budgetAlertEmail) {
      const alerts = new sns.Topic(this, 'BudgetAlerts', {
        displayName: 'JobLens budget alerts',
      });
      alerts.addSubscription(new subscriptions.EmailSubscription(props.budgetAlertEmail));

      // Not decoration. This design should cost pennies; an alert at a few dollars is how a
      // mistake — a loop, a leaked URL, a misconfigured cache — is noticed in hours rather than at
      // the end of the month. AWS Budgets is free for the first two budgets.
      new budgets.CfnBudget(this, 'MonthlyBudget', {
        budget: {
          budgetName: 'joblens-monthly',
          budgetType: 'COST',
          timeUnit: 'MONTHLY',
          budgetLimit: { amount: props.monthlyBudgetUsd, unit: 'USD' },
        },
        notificationsWithSubscribers: [
          {
            notification: {
              notificationType: 'ACTUAL',
              comparisonOperator: 'GREATER_THAN',
              threshold: 50,
              thresholdType: 'PERCENTAGE',
            },
            subscribers: [{ subscriptionType: 'SNS', address: alerts.topicArn }],
          },
          {
            notification: {
              notificationType: 'FORECASTED',
              comparisonOperator: 'GREATER_THAN',
              threshold: 100,
              thresholdType: 'PERCENTAGE',
            },
            subscribers: [{ subscriptionType: 'SNS', address: alerts.topicArn }],
          },
        ],
      });
    }

    new CfnOutput(this, 'SiteUrl', {
      value: `https://${distribution.distributionDomainName}`,
      description: 'The application. The API is at /api/v1 on the same origin.',
    });
    new CfnOutput(this, 'DistributionId', {
      value: distribution.distributionId,
      description: 'Needed to invalidate the cache after a frontend deploy.',
    });
  }

  /**
   * Headers for the static shell. The API sets its own — including no-store — and those are not
   * touched, because this policy applies only to the default behaviour.
   */
  private siteHeadersPolicy(): cloudfront.ResponseHeadersPolicy {
    return new cloudfront.ResponseHeadersPolicy(this, 'SiteHeaders', {
      securityHeadersBehavior: {
        contentTypeOptions: { override: true },
        frameOptions: { frameOption: cloudfront.HeadersFrameOption.DENY, override: true },
        referrerPolicy: {
          referrerPolicy: cloudfront.HeadersReferrerPolicy.NO_REFERRER,
          override: true,
        },
        strictTransportSecurity: {
          accessControlMaxAge: Duration.days(365),
          includeSubdomains: true,
          override: true,
        },
        contentSecurityPolicy: {
          // The SPA loads its own bundle and calls its own origin. Nothing else.
          contentSecurityPolicy: [
            "default-src 'none'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "base-uri 'none'",
            "form-action 'none'",
            "frame-ancestors 'none'",
          ].join('; '),
          override: true,
        },
      },
    });
  }
}
