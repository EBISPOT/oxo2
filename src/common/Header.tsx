import { Link, useLocation } from "react-router-dom";

export default function Header() {
  const location = useLocation();
  const page = location.pathname.split("/", 2)[1];

  return (
    <header
      className="bg-black bg-right bg-cover"
      style={{
        backgroundImage:
          "url('" + process.env.PUBLIC_URL + "/embl-ebi-background.jpg')",
      }}
    >
      <div className="container mx-auto flex flex-row gap-10">
        <div className="py-6">
          <a href={process.env.PUBLIC_URL}>
            <img
              alt="OLS logo"
              className="h-24 inline-block"
              src={process.env.PUBLIC_URL + "/logo.png"}
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
