import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import './index.css';
import App from './App';
import reportWebVitals from "./reportWebVitals";
import {StrictMode} from "react";

const root = createRoot(
    document.getElementById("root") as HTMLElement
);

root.render(
    <BrowserRouter basename={import.meta.env.OXO_PUBLIC_URL}>
        <StrictMode>
            <App />
        </StrictMode>
    </BrowserRouter>
);



// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
