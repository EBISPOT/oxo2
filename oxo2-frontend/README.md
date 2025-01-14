## OXO2

UI for OxO v2

## Development: Running OXO2 using Docker

This will require Docker.
In the project directory, you can run:

    docker compose up --force-recreate --build oxo2

This will build and run `oxo2` service on port `8081`.
Open [http://localhost:8081/spot/oxo2](http://localhost:8081/spot/oxo2) to view it in the browser.

## Development: Running OXO2 locally

This will require either NPM or Yarn to run the following commands inside the project directory.

### Install

    npm install
or

    yarn install

These download dependencies defined in the project and generates a `node_modules` folder with the installed dependencies.

### Run

    npm start
or

    yarn start

These run the app in the development mode.
Open [http://localhost:3000/spot/oxo2](http://localhost:3000/spot/oxo2) to view it in the browser.
The page will reload if you make edits.
You will also see any lint errors in the console.

### Build

    npm run build
or

    yarn build

These build the app for production to the `build` folder.
It correctly bundles React in production mode and optimizes the build for the best performance.
