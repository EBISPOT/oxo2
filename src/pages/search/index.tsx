import { KeyboardArrowDown } from "@mui/icons-material";
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
        <div className="flex flex-row gap-4 mt-8 mb-4">
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
          <button className="button-primary text-lg font-bold">Search</button>
        </div>
        <div className="grid grid-cols-4 gap-8">
          <div className="col-span-1">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 text-neutral-black">
              <div className="font-semibold text-lg mb-2">Mapping Provider</div>
              <fieldset className="mb-4">
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">Monarch Initiative</span>
                </label>
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">EBI</span>
                </label>
              </fieldset>
              <div className="font-semibold text-lg mb-2">
                Mapping Justification
              </div>
              <fieldset className="mb-4">
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">Lexical</span>
                </label>
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">Manual curation</span>
                </label>
              </fieldset>
              <div className="font-semibold text-lg mb-2">Object Prefix</div>
              <fieldset className="mb-4">
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">MONDO</span>
                </label>
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">HP</span>
                </label>
              </fieldset>
              <div className="font-semibold text-lg mb-2">Mapping Set</div>
              <fieldset className="mb-4">
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">Mapping Set 1</span>
                </label>
                <label className="block p-1 w-fit">
                  <input type="checkbox" className="invisible hidden peer" />
                  <span className="input-checkbox mr-4" />
                  <span className="capitalize mr-4">Mapping Set 2</span>
                </label>
              </fieldset>
            </div>
          </div>
          <div className="col-span-3">
            <div className="flex flex-row justify-between">
              <div className="flex flex-col justify-center">
                <label>Confidence</label>
                <input
                  type="range"
                  min="1"
                  max="3"
                  className="apperance-none bg-transparent accent-petrol-default cursor-pointer"
                  defaultValue={2}
                />
              </div>
              <div className="justify-end flex flex-row gap-4">
                <div className="flex group relative text-md">
                  <select className="input-default appearance-none pr-7 z-20 bg-transparent">
                    <option>Predicate</option>
                    <option>Option 1</option>
                    <option>Option 2</option>
                  </select>
                  <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                    <KeyboardArrowDown fontSize="medium" />
                  </div>
                </div>
                <div className="flex group relative text-md">
                  <select className="input-default appearance-none pr-7 z-20 bg-transparent">
                    <option>Download as...</option>
                    <option>Option 1</option>
                    <option>Option 2</option>
                  </select>
                  <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                    <KeyboardArrowDown fontSize="medium" />
                  </div>
                </div>
                <div className="flex group relative text-md">
                  <label className="self-center px-3">Show</label>
                  <select className="input-default appearance-none pr-7 z-20 bg-transparent">
                    <option>10</option>
                    <option>25</option>
                    <option>100</option>
                  </select>
                  <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                    <KeyboardArrowDown fontSize="medium" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
