import { Link, useLocation } from "react-router-dom";
import urlJoin from "url-join";

export default function Header() {
  const location = useLocation();
  const page = location.pathname.split("/", 2)[1];

  return (
    <header
      className="bg-black bg-right bg-cover"
      style={{
        backgroundImage:
          "url('" + urlJoin(process.env.PUBLIC_URL!, "/embl-ebi-background.jpg") + "')"
      }}
    >
      <div className="container mx-auto flex flex-col md:flex-row md:gap-10">
        <div className="py-6 self-center">
          <a href={urlJoin(process.env.PUBLIC_URL!, "/")}>
            <img
              alt="OxO logo"
              className="h-24 inline-block"
              src={urlJoin(process.env.PUBLIC_URL!, "/logo.png")}
            />
          </a>
        </div>
        <nav className="self-center">
          <ul className="bg-transparent text-white flex divide-white divide-x">
            <Link to="/home">
              <li
                className={`rounded-l-md px-4 py-3 ${
                  page === "" || page === "home"
                    ? "bg-opacity-75 bg-neutral-500"
                    : ""
                }`}
              >
                Home
              </li>
            </Link>
            <Link to={`/docs`}>
              <li
                className={`px-4 py-3 ${
                  page === "docs" ? "bg-opacity-75 bg-neutral-500" : ""
                }`}
              >
                Documentation
              </li>
            </Link>
            <Link to={`/about`}>
              <li
                className={`rounded-r-md px-4 py-3 ${
                  page === "about" ? "bg-opacity-75 bg-neutral-500" : ""
                }`}
              >
                About
              </li>
            </Link>
          </ul>
        </nav>
      </div>
    </header>
  );
}
