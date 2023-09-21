## OXO2

UI for OxO v2

## Development: Running OXO2 using Docker

This will require Docker.
In the project directory, you can run:

    docker compose up --force-recreate --build oxo2

This will build and run `oxo2` service on port `8081`.
Open [http://localhost:8081/spot/oxo2](http://localhost:8081/spot/oxo2) to view it in the browser.

## Development: Running OXO2 locally

This will require either NPM or Yarn.
In the project directory, you can run:

    npm install

    yarn install

Downloads dependencies defined in the project and generates a `node_modules` folder with the installed dependencies.

    npm start

    yarn start

Runs the app in the development mode.
Open [http://localhost:3000/spot/oxo2](http://localhost:3000/spot/oxo2) to view it in the browser.

The page will reload if you make edits.
You will also see any lint errors in the console.

    npm run build

    yarn build

Builds the app for production to the `build` folder.
It correctly bundles React in production mode and optimizes the build for the best performance.
