def call(body) {
    def config = [
            versions: ['4.4']
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node {
        stage 'Lint'
        checkout scm
        sh 'ls -l'

        def nodeBuilds = [:]
        for (def nodeVersion : config.versions) {
            def thisVersion = nodeVersion
            nodeBuilds["node${thisVersion}"] = {
                node {
                    checkout scm
                    stage "Tests ${thisVersion}"
                    print "Node build ${thisVersion}!"
                    sh 'ls -l'
                }
            }
        }
        parallel nodeBuilds

        stage 'Build'
        node {
            checkout scm
            sh '''NPM_VERSION=`cat package.json | jq -r '.version'`
VERSION_COMMIT=`git rev-list -n 1 $NPM_VERSION 2>/dev/null`
CURRENT_COMMIT=`git log -1 --pretty=format:%H`
echo $NPM_VERSION
echo $VERSION_COMMIT
echo $CURRENT_COMMIT
if [ "$CURRENT_COMMIT" != "$VERSION_COMMIT" ]; then exit 1; fi
'''
        }

        stage 'Deploy'
        node {
            sh 'echo deploy'
        }

        stage 'post'
        print "Goodbye"
    }
}

