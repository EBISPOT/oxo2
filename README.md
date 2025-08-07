# OxO2
A SSSOM compliant implementation of OxO that is backwards compatible with OxO version 1

## Running OxO2 using docker
### Environment variables
The following environment variables must be set:
1. OXO2_DATA - This where SSSOM files will be downloaded to and where any output of the OxO2 dataload will be written.
2. OXO_CONFIG - Points to the OxO2 config file. This is how OxO2 knows which SSSOM mappings to load. For an example see the


## Running OxO2 from the commandline. 

### Prerequisites 
Ensure the following software is installed and available on the user path.
1. Java 17 or later
2. Maven 3.x
3. Git
4. Solr 9.x - Ensure SOLR_HOME is set to point to the root directory of your Solr installation.
5. Nemo - download the latest version from [Nemo latest](https://github.com/knowsys/nemo/releases/latest) or build from source
 following instructions [here](https://github.com/knowsys/nemo?tab=readme-ov-file#installation). To check your Nemo installation,
run `nmo --help`. 

### Environment variables
Define the following environment variables:
1. OXO2_DATA - This where SSSOM files will be downloaded to and where any output of the OxO2 dataload will be written.
2. OXO2_CONFIG - Points to the OxO2 config file. This is how OxO2 knows which SSSOM mappings to load. For an example see the
oxo-config.json in the root of the OxO2 source code directory.

### Steps
1. Checkout OxO2:
`git clone git@github.com:EBISPOT/oxo2.git`
and change to OxO2 source directory.
2. To build, run: `mvn clean install` 
3. Copy solr config to solr: `cp ./oxo2-dataload/solr-config/* $SOLR_HOME/server/solr/configsets`
4. Change to dataload directory: `cd ./oxo2-dataload`
5. Run OxO2 dataload: `./oxo2-dataload/loadData.sh`
6. Run OxO backend: `./startBackend.sh`
7. To build and run frontend: 
   1. Change directory to frontend: `cd oxo2-frontend`
   2. Build frontend: `npm install`
   3. Start frontend: `npm run dev`
   4. Access from browser at: `http://localhost:5173/`
 

