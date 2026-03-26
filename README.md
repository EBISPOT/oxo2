# OxO2
A SSSOM compliant implementation of OxO that is backwards compatible with OxO version 1

## Running OxO2 using Docker

## Prerequisites
1. Ensure docker is installed in your environment.

### Environment variables
1. Set OXO2_CONFIG environment variable - It should point to the OxO2 config file. This is how OxO2 knows which SSSOM 
mappings to load. For an example config file see the `oxo-config.json` in the root of the OxO2 code base.
Copy your desired configuration file into the root directory of OXO2. E.g. if your configuration file 
is `my_oxo2_config.json`, set OXO2_CONFIG=./my_oxo2_config.json by running `export OXO2_CONFIG=./my_oxo2_config.json`.

### Steps
1. To run OxO2 using docker, run `docker compose up`. This will start Solr, the OxO2 backend and OxO2 frontend. The frontend
will be available at `http://localhost:8080`, the backend at `http://localhost:8081` and Solr at `http://localhost:8983`.
2. To stop OxO2 run `docker compose down`.

## Running OxO2 locally in Kubernetes using Minikube
### Prerequisites
1. Ensure you have a kubernetes cluster available. E.g. for a local k8s cluster using `minikube` run `minikube start --driver=docker`.  
2. Ensure you have helm installed.
 
### Environment variables
1. Ensure that you have a data release such that have the complete OxO2 available at a $SOLR_HOME.

### Mount your data into minikube
1. Mount into minikube: 
`minikube mount --port=39000 $SOLR_HOME:/mnt/oxo/solr-data`.

***Note***: You may need to allow port 39000 through firewall. If so, run: `sudo ufw allow 39000/tcp`.


### Deployment
1. From the root directory run: 
```
helm install oxo2 ./k8chart-local/oxo2 -n ontotools --create-namespace
```    

2. To access locally run: 
```
kubectl port-forward deployment/oxo2-frontend 8080:8080 -n ontotools
kubectl port-forward deployment/oxo2-backend 8081:8081 -n ontotools
kubectl port-forward deployment/oxo2-solr 8983:8983 -n ontotools
``` 
and the point your browser to http://localhost:8080. The OxO2 backend will be accessible at http://localhost:8081/ and solr at http://localhost:8983.


### To undeploy
1. Run: `helm uninstall oxo2 -n ontotools`.


## Running OxO2 locally from the commandline. 
### Prerequisites 
Ensure the following software is installed and available on the user path.
1. Java 17 or later
2. Maven 3.x
3. Git
4. Solr 9.x - Ensure SOLR_SCRIPT is set to /bin dir SOLR_HOME is set to /server/solr dir of your Solr installation.
5. Nemo - download the latest version from [Nemo latest](https://github.com/knowsys/nemo/releases/latest) or build from source
 following instructions [here](https://github.com/knowsys/nemo?tab=readme-ov-file#installation). To check your Nemo installation, run `nmo --help`. Ensure that `nmo` is available on 
the path. Nemo is the rules engine used in OxO2.
6. Optionally you could use Nextflow for parallelising the OxO2 dataload. Nextflow can be installed using `curl -s https://get.nextflow.io | bash`.
   Make sure to add it to your path.

### Environment variables
Define the following environment variables:
1. OXO2_DATA - This is where SSSOM files will be downloaded to and where any output of the OxO2 dataload will be written.
2. OXO2_CONFIG - Points to the OxO2 config file. This is how OxO2 knows which SSSOM mappings to load. For an example config
file see the `oxo-config.json` in the root of the OxO2 source code directory. NOTE: This must be the absolute path to the file.
3. SOLR_SCRIPT - This should point to the `bin` directory of your Solr installation.
4. SOLR_HOME - This should point to the root of your Solr data directory.
5. OXO2_SOLR_HOST - This is the URL to your Solr installation.
6. NEXTFLOW_DIR - If you want to parallelise the OxO2 dataload using Nextflow, you need to specify where it can write interm results. 

Here is an example script for setting environment variables:

    export SOLR_SCRIPT=/home/myhome/solr-9.9.0/bin
    export SOLR_HOME=/home/myhome/oxo2-data/solr
    export PATH=$PATH:/home/myhome/nemo
    export OXO2_DATA=/home/myhome/oxo2-data/dataload
    export JAVA_OPTS="-Xmx16G"
    export OXO2_CONFIG=/home/myhome/oxo2/my_oxo2_config.json
    export OXO2_SOLR_HOST=http://localhost:8983/solr
    export NEXTFLOW_DIR=/home/myhome/nextflow

### Steps
1. Checkout OxO2:
`git clone git@github.com:EBISPOT/oxo2.git`
and change to OxO2 source directory.
2. To build, run: `mvn clean install` 
3. Copy solr config to solr: `cp ./oxo2-dataload/solr-config/* $SOLR_HOME`
4. Change to dataload directory: `cd ./oxo2-dataload`
5. Run OxO2 dataload: `./loadData.sh` or use `loadData.nextflow` if you have Nextflow installed.
6. Return to OxO2 root dir: `cd ..`
7. Start Solr: `$SOLR_SCRIPT/solr start --user-managed`
8. Run OxO backend: `./startBackend.sh`
9. To build and run frontend: 
   1. Change directory to frontend: `cd oxo2-frontend`
   2. Build frontend: `npm install`
   3. Start frontend: `npm run dev`
   4. Access frontend from browser at: `http://localhost:8080/`
   5. Backend is accessible at: `http://localhost:8081`.
 

