import { useState } from "react";
import { useLocation } from "react-router-dom";
import Header from "../../common/Header";

export default function Search() {
  const location = useLocation();
  const passedQuery =
    location.state?.search && Array.isArray(location.state.search)
      ? location.state.search.join("\n")
      : "";

  const [query, setQuery] = useState<string>(passedQuery);

  return (
    <div>
      <Header />
      <main className="container mx-auto">
        <div className="flex flex-row gap-4 mt-8">
          <div className="w-full">
            <textarea
              id="home-search"
              rows={4}
              style={{ resize: "vertical", minHeight: "5rem" }}
              placeholder={"Search OxO..."}
              className="input-default text-lg"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
              }}
            />
          </div>
          <button className="button-primary text-lg font-bold">
            Search
          </button>
        </div>
      </main>
    </div>
  );
}
