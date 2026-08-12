import { Duration, RemovalPolicy, Stack, StackProps, CfnOutput } from 'aws-cdk-lib';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import { Construct } from 'constructs';

/**
 * The container registry, on its own so that the image can exist before anything tries to run it.
 *
 * <p>Deploying this stack creates an empty repository, which costs nothing until an image is
 * pushed. The application stack then references an image by tag, which is why the two are separate:
 * a single stack cannot both create the repository and depend on an image inside it.
 */
export class JoblensRegistryStack extends Stack {
  readonly backendRepository: ecr.Repository;

  constructor(scope: Construct, id: string, props: StackProps) {
    super(scope, id, props);

    this.backendRepository = new ecr.Repository(this, 'BackendRepository', {
      repositoryName: 'joblens-backend',
      imageTagMutability: ecr.TagMutability.IMMUTABLE,
      imageScanOnPush: true,
      encryption: ecr.RepositoryEncryption.AES_256,
      // Storage is charged per gigabyte-month, and old images are the easiest cost to forget.
      // The catch-all rule has to sort last, which ECR expresses as the highest priority number.
      lifecycleRules: [
        {
          description: 'Delete untagged layers after a day.',
          tagStatus: ecr.TagStatus.UNTAGGED,
          maxImageAge: Duration.days(1),
          rulePriority: 1,
        },
        {
          description: 'Keep the ten most recent images.',
          maxImageCount: 10,
          rulePriority: 2,
        },
      ],
      // Destroying the stack should leave nothing behind that keeps charging.
      removalPolicy: RemovalPolicy.DESTROY,
      emptyOnDelete: true,
    });

    new CfnOutput(this, 'BackendRepositoryUri', {
      value: this.backendRepository.repositoryUri,
      description: 'Push the backend image here before deploying the application stack.',
    });
  }
}
