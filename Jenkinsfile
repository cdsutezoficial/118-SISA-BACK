pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    triggers {
        githubPush()
    }

    environment {
        COMPOSE_PROJECT_NAME = 'sisa-back'
        COMPOSE_FILE        = 'docker-compose.yml'
        ENV_FILE_CDS        = credentials('sisa-back-env-cds')

        JENKINS_URL     = 'https://testdti.utez.edu.mx:8443'
        PROJECT_NAME    = '118-SISA-BACK'
        GIT_REPO        = 'https://github.com/cdsutezoficial/118-SISA-BACK.git'
        GIT_CREDENTIALS = 'CDSUTEZ'
        GIT_BRANCH      = 'test'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                git branch: env.GIT_BRANCH, credentialsId: env.GIT_CREDENTIALS, url: env.GIT_REPO
            }
        }

        // El compose carga ${SISA_ENV_FILE} como env_file del API con ruta relativa
        // al repo; se sobrescribe con la ruta de la credencial en cada comando.
        stage('Validar configuración') {
            steps {
                echo 'Validando docker-compose con el archivo de entorno...'
                sh 'SISA_ENV_FILE=$ENV_FILE_CDS docker compose --env-file $ENV_FILE_CDS -f docker-compose.yml config -q'
            }
        }

        stage('Detener servicios') {
            steps {
                echo 'Deteniendo servicios existentes...'
                // Sin -v: el volumen de MySQL se conserva entre despliegues.
                sh 'SISA_ENV_FILE=$ENV_FILE_CDS docker compose --env-file $ENV_FILE_CDS -f docker-compose.yml down --remove-orphans || true'
                sh '''
                    # 1) Eliminar contenedores del proyecto por nombre (cualquier estado)
                    for CONTAINER in api-sisa db-sisa; do
                        if docker ps -a --format "{{.Names}}" | grep -q "^${CONTAINER}$"; then
                            echo "Eliminando contenedor $CONTAINER"
                            docker rm -f "$CONTAINER" || true
                        fi
                    done

                    # 2) Eliminar contenedores que ocupen los puertos (cualquier estado)
                    API_PORT=$(grep -E '^API_PORT=' "$ENV_FILE_CDS" | tail -n1 | cut -d= -f2- | tr -d '\\r" ')
                    DB_PORT=$(grep -E '^DB_PORT=' "$ENV_FILE_CDS" | tail -n1 | cut -d= -f2- | tr -d '\\r" ')
                    for PORT in $API_PORT $DB_PORT; do
                        CONTAINER=$(docker ps -a --format "{{.ID}} {{.Ports}}" | grep ":${PORT}->" | awk '{print $1}')
                        if [ -n "$CONTAINER" ]; then
                            echo "Puerto $PORT ocupado por contenedor $CONTAINER — eliminando"
                            docker rm -f $CONTAINER || true
                        fi
                    done
                '''
            }
        }

        stage('Limpiar recursos Docker') {
            steps {
                echo 'Limpiando imágenes antiguas del proyecto SISA-BACK...'
                sh '''
                    # Eliminar solo las imágenes antiguas de este proyecto
                    docker images | grep sisa-api | grep -v latest | awk '{print $3}' | xargs -r docker rmi -f || true

                    # Limpiar solo imágenes dangling (sin etiqueta)
                    docker image prune -f
                '''
            }
        }

        stage('Construir imágenes') {
            steps {
                echo 'Construyendo imágenes sin caché...'
                sh 'SISA_ENV_FILE=$ENV_FILE_CDS docker compose --env-file $ENV_FILE_CDS -f docker-compose.yml build --no-cache'
            }
        }

        stage('Levantar servicios') {
            steps {
                echo 'Levantando servicios...'
                sh 'SISA_ENV_FILE=$ENV_FILE_CDS docker compose --env-file $ENV_FILE_CDS -f docker-compose.yml up -d'
            }
        }

        stage('Verificar servicios') {
            steps {
                echo 'Verificando estado de los servicios...'
                sh 'SISA_ENV_FILE=$ENV_FILE_CDS docker compose --env-file $ENV_FILE_CDS -f docker-compose.yml ps'
                sh 'SISA_ENV_FILE=$ENV_FILE_CDS docker compose --env-file $ENV_FILE_CDS -f docker-compose.yml logs --tail=20'
            }
        }

        stage('Healthcheck') {
            steps {
                echo 'Verificando salud de los servicios...'
                sh '''
                    sleep 10
                    docker inspect --format='{{.State.Health.Status}}' db-sisa || echo "Sin healthcheck"
                    docker inspect --format='{{.State.Status}}' api-sisa
                '''
            }
        }
    }

    post {
        success {
            echo 'OK'
        }

        failure {
            echo 'NO'
        }
    }
}
