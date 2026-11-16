// Shared steps (publishApkForUpdate, buildAndPushIfChanged, removeRootOwnedPaths, withApkSigningKey):
// github sergesvalov/jenkins-shared-library, set up in Jenkins as mylib.
@Library('mylib@main') _

pipeline {
    // Вся сборка выполняется на агенте Raspberry Pi в Docker-контейнерах
    agent { label 'rpi && docker' }

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 60, unit: 'MINUTES')
    }

    parameters {
        choice(
            name: 'DEPLOY_METHOD',
            choices: ['DIRECT_SSH', 'NONE'],
            description: 'DIRECT_SSH: поднять Docker-сервис тестов на 192.168.0.225:8085 и выгрузить APK на 192.168.0.230; NONE: только сборка в Docker'
        )
        choice(
            name: 'BUILD_FLAVOR',
            choices: ['allInOne', 'ALL', 'orienteering', 'reptiles', 'languages'],
            description: 'Какой APK собирать. По умолчанию один универсальный allInOne (тесты скачиваются с сервера). ALL — все 4 варианта'
        )
        booleanParam(name: 'BUILD_ANDROID', defaultValue: true, description: 'Собрать Android APK в Docker')
        booleanParam(name: 'BUILD_AAB', defaultValue: false, description: 'Дополнительно собрать AAB (нужен только для Google Play)')
        booleanParam(name: 'BUILD_WEB_DOCKER', defaultValue: true, description: 'Собрать и запушить Docker-образ сервиса тестов (Dockerfile.web)')
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Запускать проверку JSON-курсов и unit-тесты в Docker')
    }

    environment {
        // Локальный Docker Registry (сеть 192.168.0.0/24)
        REGISTRY_IP   = "192.168.0.222"
        REGISTRY_PORT = "5050"
        BUILDER_IMAGE = "${REGISTRY_IP}:${REGISTRY_PORT}/alltestsareus-android-builder"
        WEB_IMAGE     = "${REGISTRY_IP}:${REGISTRY_PORT}/alltestsareus-web"

        // Сервер сервиса тестов (откуда Android скачивает тесты в Docker)
        WEB_SERVER_IP   = "192.168.0.225"
        WEB_SERVER_PORT = "8085"
        WEB_CATALOG_URL = "http://192.168.0.225:8085/catalog.json"
        // Сервер 192.168.0.225 — x86_64, а агент сборки — Raspberry Pi (arm64)
        WEB_PLATFORM    = "linux/amd64"

        // Сервер публикации APK
        PROD_SERVER_IP = "192.168.0.230"
        APK_REPO_URL   = "http://192.168.0.230:8060"
        APK_REPO_APP   = "alltestsareus"
        DEPLOY_USER    = "${env.SERVER_USER ?: 'jenkins'}"

        // Агент работает на ARM64, где aapt2 из Maven не запускается — используем системный /usr/bin/aapt2
        GRADLE_ARGS = "-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2 --build-cache --configure-on-demand"
    }

    stages {
        stage('Version') {
            steps {
                script {
                    env.VERSION_CODE = sh(returnStdout: true, script: 'echo $(( ($(git log -1 --format=%ct) - 1704067200) / 60 ))').trim()
                    env.VERSION = "1.0.${env.VERSION_CODE}"
                    echo "Версия сборки: ${env.VERSION} (versionCode=${env.VERSION_CODE})"
                    // Убираем артефакты прошлых сборок (могли остаться root-owned из Docker)
                    removeRootOwnedPaths(dir: '.', paths: ['out'])
                    sh 'mkdir -p out'
                }
            }
        }

        stage('Build & Push Builder Image') {
            steps {
                script {
                    buildAndPushIfChanged(
                        image: env.BUILDER_IMAGE,
                        dockerfile: 'Dockerfile.android',
                        label: 'Android Builder'
                    )
                }
            }
        }

        stage('Validate Courses & Run Tests (Docker)') {
            when {
                expression { params.RUN_TESTS }
            }
            steps {
                script {
                    echo "Проверка JSON-курсов и запуск unit-тестов внутри Docker..."
                    withBuilder {
                        sh "PYTHONIOENCODING=utf-8 python3 tools/validate_courses.py"
                        sh "gradle :app:testAllInOneDebugUnitTest ${GRADLE_ARGS}"
                    }
                }
            }
            post {
                always {
                    junit testResults: '**/build/test-results/**/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Build & Deploy Tests Web Service (Docker -> 192.168.0.225)') {
            when {
                expression { params.BUILD_WEB_DOCKER }
            }
            steps {
                script {
                    echo "Сборка Docker-образа веб-сервиса тестов (${env.WEB_IMAGE}:${env.VERSION})..."
                    sh """
                        DOCKER_BUILDKIT=1 docker build --platform ${env.WEB_PLATFORM} \\
                          -t ${env.WEB_IMAGE}:latest -t ${env.WEB_IMAGE}:${env.VERSION} -f Dockerfile.web .
                        docker image inspect -f 'Образ веб-сервиса: {{.Os}}/{{.Architecture}}' ${env.WEB_IMAGE}:latest
                        docker push ${env.WEB_IMAGE}:latest
                        docker push ${env.WEB_IMAGE}:${env.VERSION}
                    """

                    if (params.DEPLOY_METHOD == 'DIRECT_SSH') {
                        echo "Запуск Docker-сервиса тестов на ${env.WEB_SERVER_IP}:${env.WEB_SERVER_PORT}..."
                        sshagent(credentials: [env.DEPLOY_USER]) {
                            sh """
                                ssh -o StrictHostKeyChecking=accept-new ${env.DEPLOY_USER}@${env.WEB_SERVER_IP} '
                                    set -e
                                    docker pull --platform ${env.WEB_PLATFORM} ${env.WEB_IMAGE}:${env.VERSION}
                                    docker rm -f alltestsareus-web >/dev/null 2>&1 || true
                                    docker run -d --name alltestsareus-web --restart unless-stopped \\
                                      --platform ${env.WEB_PLATFORM} \\
                                      -p ${env.WEB_SERVER_PORT}:80 ${env.WEB_IMAGE}:${env.VERSION}
                                '
                            """
                        }

                        echo "Проверка доступности ${env.WEB_CATALOG_URL} и аудио..."
                        sh """
                            ok=0
                            for i in \$(seq 1 15); do
                                if curl -fsS -o /dev/null "${env.WEB_CATALOG_URL}"; then ok=1; break; fi
                                sleep 2
                            done
                            if [ "\$ok" != "1" ]; then
                                echo "Сервис тестов не отвечает на ${env.WEB_CATALOG_URL}"
                                exit 1
                            fi
                            curl -fsS "${env.WEB_CATALOG_URL}" | head -c 300; echo
                            curl -fsS -o /dev/null -w "MP3 озвучка: HTTP %{http_code}, %{size_download} байт\\n" \\
                              "http://${env.WEB_SERVER_IP}:${env.WEB_SERVER_PORT}/audio/greek/01_kano.mp3"
                        """
                    }
                }
            }
        }

        stage('Compile Android APK (Docker)') {
            when {
                expression { params.BUILD_ANDROID }
            }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    script {
                        def flavors = (params.BUILD_FLAVOR == 'ALL')
                            ? ['allInOne', 'orienteering', 'reptiles', 'languages']
                            : [params.BUILD_FLAVOR]

                        def gradleTasks = flavors.collect { f ->
                            def cap = f[0].toUpperCase() + f.substring(1)
                            params.BUILD_AAB ? "assemble${cap}Release bundle${cap}Release" : "assemble${cap}Release"
                        }.join(' ')

                        echo "Сборка Android в Docker (${flavors.join(', ')}): ${gradleTasks}"

                        withApkSigningKey(required: false) {
                            def signArgs = (env.APK_SIGNING == 'true' && env.APK_KEYSTORE_FILE)
                                ? "-v ${env.APK_KEYSTORE_FILE}:/signing/release.keystore:ro -e APK_KEYSTORE_FILE=/signing/release.keystore -e APK_KEYSTORE_PASSWORD -e APK_KEY_ALIAS"
                                : ""
                            withBuilder(signArgs) {
                                sh """
                                    gradle ${gradleTasks} ${GRADLE_ARGS} -PversionCode=${env.VERSION_CODE} -PwebCatalogUrl=${env.WEB_CATALOG_URL} || { chmod -R a+rwX app/build || true; exit 1; }
                                    chmod -R a+rwX app/build
                                """
                            }
                        }

                        flavors.each { f ->
                            def apkPattern = "app/build/outputs/apk/${f}/release/*.apk"
                            def foundApks = findFiles(glob: apkPattern)
                            if (foundApks.length == 0) {
                                error("Android APK не найден для flavor '${f}' (${apkPattern})")
                            }
                            def apkFile = foundApks[0].path
                            def baseName = (f == 'allInOne') ? "AllTestsAreUs-${env.VERSION}" : "AllTestsAreUs-${f}-${env.VERSION}"
                            collect(apkFile, "${baseName}.apk")

                            if (params.BUILD_AAB) {
                                def foundAabs = findFiles(glob: "app/build/outputs/bundle/${f}Release/*.aab")
                                if (foundAabs.length > 0) {
                                    collect(foundAabs[0].path, "${baseName}.aab")
                                }
                            }

                            if (f == 'allInOne' && params.DEPLOY_METHOD == 'DIRECT_SSH') {
                                publishApkForUpdate(
                                    apk: apkFile,
                                    versionName: env.VERSION,
                                    host: env.PROD_SERVER_IP,
                                    user: env.DEPLOY_USER,
                                    credentialsId: env.DEPLOY_USER,
                                    remoteDir: '/opt/alltestsareus/downloads'
                                )
                            }
                        }
                    }
                }
            }
        }

        stage('Publish APK to APK Repo') {
            when {
                expression {
                    params.BUILD_ANDROID &&
                    params.DEPLOY_METHOD == 'DIRECT_SSH' &&
                    (params.BUILD_FLAVOR == 'ALL' || params.BUILD_FLAVOR == 'allInOne')
                }
            }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    script {
                        def foundApks = findFiles(glob: 'app/build/outputs/apk/allInOne/release/*.apk')
                        if (foundApks.length > 0) {
                            def apkPath = foundApks[0].path
                            def gitShort = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                            echo "Публикация APK (${env.VERSION}) в apk_repo (${env.APK_REPO_URL})..."
                            withCredentials([string(credentialsId: 'apk-publish-token', variable: 'APK_TOKEN')]) {
                                sh """
                                    HTTP_CODE=\$(curl -sSL -o /tmp/apk_repo_resp.json -w "%{http_code}" -X POST "${env.APK_REPO_URL}/api/v1/apps/${env.APK_REPO_APP}/upload" \\
                                      -H "Expect:" \\
                                      -H "X-Publish-Token: \${APK_TOKEN}" \\
                                      -F "file=@${apkPath}" \\
                                      -F "app_id=${env.APK_REPO_APP}" \\
                                      -F "title=AllTestsAreUs" \\
                                      -F "package_name=com.alltestsareus.app" \\
                                      -F "version_code=${env.VERSION_CODE}" \\
                                      -F "version_name=${env.VERSION}" \\
                                      -F "build_number=${env.BUILD_NUMBER}" \\
                                      -F "source=jenkins" \\
                                      -F "changelog=Jenkins build #${env.BUILD_NUMBER} (${gitShort})")
                                    cat /tmp/apk_repo_resp.json || true
                                    rm -f /tmp/apk_repo_resp.json
                                    if [ "\$HTTP_CODE" -lt 200 ] || [ "\$HTTP_CODE" -ge 300 ]; then
                                        echo "Ошибка публикации в apk_repo: HTTP \$HTTP_CODE"
                                        exit 1
                                    fi
                                """
                            }
                        } else {
                            echo "allInOne APK не найден, пропуск публикации в apk_repo."
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'out/*', allowEmptyArchive: true, fingerprint: true
            removeRootOwnedPaths(dir: '.', paths: ['app/build', '.gradle', 'out'])
        }
        success {
            echo "Сборка и деплой AllTestsAreUs завершены успешно!"
        }
        failure {
            echo "Сборка AllTestsAreUs не удалась. Проверьте лог выше."
        }
    }
}

def collect(String path, String name) {
    sh "mkdir -p out && cp '${path}' 'out/${name}'"
}

def withBuilder(String extraArgs = '', Closure body) {
    docker.image("${env.BUILDER_IMAGE}:latest").inside("-u root -v alltestsareus-gradle-home:/root/.gradle ${extraArgs}".trim()) {
        body()
    }
}
