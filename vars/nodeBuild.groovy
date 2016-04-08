def call(body) {
    def config = [
            versions: ['4.4']
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def registry = 'registry.private.com'
    def imageName = "${config.name}:jenkins-${env.BUILD_ID}"
    def containerName = config.name.replaceAll('-', '') + env.BUILD_ID

    node {
        // Build container:
        stage 'Build'
        checkout scm
        sh "docker build -t ${imageName} ."

        def nodeChecks = [:]
        nodeChecks['Lint'] = {
            node {
                stage 'Lint'
                sh "docker run --rm ${imageName} npm run lint"
            }
        }
        nodeChecks['Test'] = {
            node {
                stage 'Test'
                sh """docker rm ${containerName} || true
docker run --name ${containerName} ${imageName} npm run test
mkdir -p build/
docker cp ${containerName}:/app/build/tests.xml build/tests.xml
docker rm ${containerName}
"""
                step([$class: 'JUnitResultArchiver', testResults: '**/build/tests.xml'])
            }
        }

        nodeChecks['Comments'] = {
            node {
                stage 'Comments'
                checkout scm
                step([$class: 'TasksPublisher', pattern: '**/*.js', high: 'FIXME', normal: 'TODO'])
            }
        }

        parallel nodeChecks

        if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME =~ /^release.*/) {
            stage "Publish"
            sh """NPM_VERSION=`cat package.json | jq -r '.version'`
VERSION_COMMIT=`git rev-list -n 1 \$NPM_VERSION 2>/dev/null || echo`
CURRENT_COMMIT=`git log -1 --pretty=format:%H`
if [ "\$CURRENT_COMMIT" = "\$VERSION_COMMIT" ]; then
    git verify-tag \$NPM_VERSION
    DOCKER_VERSION=\${NPM_VERSION}
elif [ "\$BRANCH_NAME" = "master" ]; then
    DOCKER_VERSION=latest
else
    DOCKER_VERSION=`echo \$BRANCH_NAME | sed 's!^release/!!g'`
fi
docker tag -f ${imageName} ${registry}/pebble/${config.name}:\${DOCKER_VERSION}
docker push ${registry}/pebble/${config.name}:\${DOCKER_VERSION}
"""
        }
    }
}

