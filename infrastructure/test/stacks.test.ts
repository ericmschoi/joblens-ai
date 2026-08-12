import * as path from 'node:path';

import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { describe, expect, it } from 'vitest';

import { JoblensApplicationStack } from '../lib/application-stack';
import { JoblensRegistryStack } from '../lib/registry-stack';

const env = { account: '123456789012', region: 'ca-central-1' };
const frontendDistPath = path.resolve(__dirname, '..', '..', 'frontend', 'dist');

function templates() {
  const app = new App();
  const registry = new JoblensRegistryStack(app, 'JoblensRegistry', { env });
  const application = new JoblensApplicationStack(app, 'JoblensApplication', {
    env,
    imageTag: 'sha-abc1234',
    frontendDistPath,
    budgetAlertEmail: 'someone@example.com',
    monthlyBudgetUsd: 5,
  });
  return {
    registry: Template.fromStack(registry),
    application: Template.fromStack(application),
  };
}

describe('the infrastructure', () => {
  it('creates nothing that charges by the hour while idle', () => {
    const { application, registry } = templates();

    // The expensive-when-idle resources in a typical AWS design. None of them belong here, and a
    // future change that introduces one should have to argue for it in this test first.
    for (const type of [
      'AWS::EC2::NatGateway',
      'AWS::EC2::Instance',
      'AWS::ElasticLoadBalancingV2::LoadBalancer',
      'AWS::RDS::DBInstance',
      'AWS::ECS::Cluster',
      'AWS::ElastiCache::CacheCluster',
      'AWS::OpenSearchService::Domain',
    ]) {
      application.resourceCountIs(type, 0);
      registry.resourceCountIs(type, 0);
    }
  });

  it('persists nothing but the site assets and the logs', () => {
    const { application } = templates();

    application.resourceCountIs('AWS::DynamoDB::Table', 0);
    application.resourceCountIs('AWS::RDS::DBCluster', 0);
    application.resourceCountIs('AWS::EFS::FileSystem', 0);
  });

  it('keeps log retention bounded', () => {
    const { application } = templates();

    application.hasResourceProperties('AWS::Logs::LogGroup', {
      RetentionInDays: 14,
    });
    application.hasResource('AWS::Logs::LogGroup', {
      DeletionPolicy: 'Delete',
    });
  });

  it('runs the API as a function with a bounded timeout and no VPC', () => {
    const { application } = templates();

    application.hasResourceProperties('AWS::Lambda::Function', {
      PackageType: 'Image',
      Architectures: ['arm64'],
      MemorySize: 2048,
      Timeout: 60,
      VpcConfig: Match.absent(),
    });
  });

  it('never exposes the function URL to the public', () => {
    const { application } = templates();

    application.hasResourceProperties('AWS::Lambda::Url', {
      AuthType: 'AWS_IAM',
    });
  });

  it('refuses to cache an API response', () => {
    const { application } = templates();

    // 4135ea2d-6df8-44a3-9df3-4b5a84be39ad is the managed CachingDisabled policy.
    application.hasResourceProperties('AWS::CloudFront::Distribution', {
      DistributionConfig: Match.objectLike({
        CacheBehaviors: Match.arrayWith([
          Match.objectLike({
            PathPattern: '/api/*',
            CachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad',
          }),
        ]),
      }),
    });
  });

  it('serves the site over HTTPS only, from a private bucket', () => {
    const { application } = templates();

    application.hasResourceProperties('AWS::CloudFront::Distribution', {
      DistributionConfig: Match.objectLike({
        DefaultCacheBehavior: Match.objectLike({
          ViewerProtocolPolicy: 'redirect-to-https',
        }),
        PriceClass: 'PriceClass_100',
      }),
    });

    application.hasResourceProperties('AWS::S3::Bucket', {
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true,
        BlockPublicPolicy: true,
        IgnorePublicAcls: true,
        RestrictPublicBuckets: true,
      },
    });
  });

  it('declares a content-security policy for the shell', () => {
    const { application } = templates();

    application.hasResourceProperties('AWS::CloudFront::ResponseHeadersPolicy', {
      ResponseHeadersPolicyConfig: Match.objectLike({
        SecurityHeadersConfig: Match.objectLike({
          ContentSecurityPolicy: Match.objectLike({
            ContentSecurityPolicy: Match.stringLikeRegexp("default-src 'none'"),
          }),
          FrameOptions: Match.objectLike({ FrameOption: 'DENY' }),
        }),
      }),
    });
  });

  it('carries no secret, and no cross-origin allowance', () => {
    const { application } = templates();

    application.hasResourceProperties('AWS::Lambda::Function', {
      Environment: {
        Variables: Match.objectLike({
          JOBLENS_ANALYSIS_PROVIDER: 'fake',
          JOBLENS_CORS_ALLOWED_ORIGINS: '',
        }),
      },
    });

    const rendered = JSON.stringify(application.toJSON());
    for (const forbidden of ['sk-', 'AKIA', 'apiKey', 'password']) {
      expect(rendered).not.toContain(forbidden);
    }
  });

  it('alerts on spending before the month ends', () => {
    const { application } = templates();

    application.hasResourceProperties('AWS::Budgets::Budget', {
      Budget: Match.objectLike({
        BudgetType: 'COST',
        TimeUnit: 'MONTHLY',
        BudgetLimit: { Amount: 5, Unit: 'USD' },
      }),
      NotificationsWithSubscribers: Match.arrayWith([
        Match.objectLike({
          Notification: Match.objectLike({ NotificationType: 'FORECASTED' }),
        }),
      ]),
    });
  });

  it('leaves nothing behind that keeps charging when the stack is destroyed', () => {
    const { application, registry } = templates();

    registry.hasResource('AWS::ECR::Repository', {
      DeletionPolicy: 'Delete',
      UpdateReplacePolicy: 'Delete',
    });
    application.hasResource('AWS::S3::Bucket', {
      DeletionPolicy: 'Delete',
      UpdateReplacePolicy: 'Delete',
    });
  });

  it('keeps old images from accumulating in the registry', () => {
    const { registry } = templates();

    registry.hasResourceProperties('AWS::ECR::Repository', {
      ImageScanningConfiguration: { ScanOnPush: true },
      LifecyclePolicy: Match.objectLike({
        // The rendered policy speaks ECR's vocabulary, not the CDK's.
        LifecyclePolicyText: Match.stringLikeRegexp('"imageCountMoreThan","countNumber":10'),
      }),
    });
  });

  it('refuses to synthesise without a built frontend', () => {
    expect(() =>
      new JoblensApplicationStack(new App(), 'Broken', {
        env,
        imageTag: 'sha-abc1234',
        frontendDistPath: path.resolve(__dirname, 'no-such-directory'),
        monthlyBudgetUsd: 5,
      }),
    ).toThrow(/Run "npm run build"/);
  });
});
