# OxO2
A SSSOM compliant implementation of OxO that is backwards compatible with OxO version 1

## Running OxO2 using docker
### Environment variables
The following environment variable must be set:
1. Set OXO2_CONFIG environment variable - It should point to the OxO2 config file. This is how OxO2 knows which SSSOM 
mappings to load. For an example config file see the `oxo-config.json` in the root of the OxO2 code base.
Copy your desired configuration file into the root directory of OXO2. E.g. if your configuration file 
is `my_oxo2_config.json`, set OXO2_CONFIG=./my_oxo2_config.json by running `export OXO2_CONFIG=./my_oxo2_config.json`.
2. To run OxO2 using docker, run `docker compose up`. This will start Solr, the OxO2 backend and OxO2 frontend. The frontend
will be available at `http://localhost:8081`, the backend at `http://localhost:8080` and Solr at `http://localhost:8983`.
3. To stop OxO2 run `docker compose down`.

## Running OxO2 from the commandline. 

### Prerequisites 
Ensure the following software is installed and available on the user path.
1. Java 17 or later
2. Maven 3.x
3. Git
4. Solr 9.x - Ensure SOLR_SCRIPT is set to /bin dir SOLR_HOME is set to /server/solr dir of your Solr installation.
5. Nemo - download the latest version from [Nemo latest](https://github.com/knowsys/nemo/releases/latest) or build from source
 following instructions [here](https://github.com/knowsys/nemo?tab=readme-ov-file#installation). To check your Nemo installation, run `nmo --help`. Ensure that `nmo` is available on 
the path.  

### Environment variables
Define the following environment variables:
1. OXO2_DATA - This is where SSSOM files will be downloaded to and where any output of the OxO2 dataload will be written.
2. OXO2_CONFIG - Points to the OxO2 config file. This is how OxO2 knows which SSSOM mappings to load. For an example config
file see the `oxo-config.json` in the root of the OxO2 source code directory. NOTE: This must be the absolute path to the file.
3. SOLR_SCRIPT - This should point to the `bin` directory of your Solr installation.
4. SOLR_HOME - This should point to the root of your Solr data directory.
5. OXO2_SOLR_HOST - This is the URL to your Solr installation.

Here is an example script for setting environment variables:

    export SOLR_SCRIPT=/home/myhome/solr-9.9.0/bin
    export SOLR_HOME=/home/myhome/oxo2-data/solr
    export PATH=$PATH:/home/myhome/nemo
    export OXO2_DATA=/home/myhome/oxo2-data/dataload
    export JAVA_OPTS="-Xmx16G"
    export OXO2_CONFIG=/home/myhome/oxo2/my_oxo2_config.json
    export OXO2_SOLR_HOST=http://localhost:8983/solr

### Steps
1. Checkout OxO2:
`git clone git@github.com:EBISPOT/oxo2.git`
and change to OxO2 source directory.
2. To build, run: `mvn clean install` 
3. Copy solr config to solr: `cp ./oxo2-dataload/solr-config/* $SOLR_HOME`
4. Change to dataload directory: `cd ./oxo2-dataload`
5. Run OxO2 dataload: `./oxo2-dataload/loadData.sh`
6. Run OxO backend: `./startBackend.sh`
7. To build and run frontend: 
   1. Change directory to frontend: `cd oxo2-frontend`
   2. Build frontend: `npm install`
   3. Start frontend: `npm run dev`
   4. Access from browser at: `http://localhost:5173/`
 

