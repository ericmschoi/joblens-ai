#!/usr/bin/env node
import * as path from 'node:path';

import { App, Tags } from 'aws-cdk-lib';

import { JoblensApplicationStack } from '../lib/application-stack';
import { JoblensRegistryStack } from '../lib/registry-stack';

/**
 * Two stacks, deployed in order, because an image has to exist before something can run it:
 *
 *   1. JoblensRegistry     — an empty ECR repository. Costs nothing until an image is pushed.
 *   2. push the image      — see docs/aws.md
 *   3. JoblensApplication  — the function, the bucket, the distribution, the budget alert.
 *
 * Synthesis needs no AWS account and no credentials: there are no context lookups anywhere in
 * these stacks, which is deliberate — infrastructure that can only be reviewed by deploying it is
 * infrastructure nobody reviews.
 */
const app = new App();

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION ?? 'ca-central-1',
};

const imageTag = app.node.tryGetContext('imageTag') ?? 'latest';
const budgetAlertEmail = app.node.tryGetContext('budgetAlertEmail');
const monthlyBudgetUsd = Number(app.node.tryGetContext('monthlyBudgetUsd') ?? 5);

new JoblensRegistryStack(app, 'JoblensRegistry', {
  env,
  description: 'JobLens container registry.',
});

new JoblensApplicationStack(app, 'JoblensApplication', {
  env,
  description: 'JobLens application: API on Lambda, SPA on S3, one CloudFront distribution.',
  imageTag,
  frontendDistPath: path.resolve(__dirname, '..', '..', 'frontend', 'dist'),
  ...(budgetAlertEmail ? { budgetAlertEmail } : {}),
  monthlyBudgetUsd,
});

Tags.of(app).add('Application', 'joblens');
Tags.of(app).add('ManagedBy', 'cdk');
