import { Route, Routes, useLocation } from "react-router";

import About from "./pages/about";
import Documentation from "./pages/documentation";
import Footer from "./components/common/Footer";
import Header from "./components/common/Header";
import Home from "./pages/home/Home";
import MappingResults from "./pages/results/MappingResults";
import MappingDetailsPage from "./pages/results/MappingDetailsPage";
import MappingSetPage from "./pages/results/MappingSetPage";
import InferencesPage from "./pages/results/InferencesPage";
import LegacyTermRedirect from "./pages/terms/LegacyTermRedirect";

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient();

function App() {
  const isSearchResults = useLocation().pathname.startsWith("/search/");

  return (
      <QueryClientProvider client={queryClient}>
          <div className="min-h-screen flex flex-col">
            <Header />
            <div className={`${isSearchResults ? "search-results-container" : "container"} mx-auto flex-1`}>
              <Routes>
                  <Route path="/" element={<Home/>} />
                  <Route path="/home" element={<Home/>} />
                  <Route path="/docs" element={<Documentation />} />
                  <Route path="/about" element={<About />} />
                  <Route path="/search/:curies" element={<MappingResults /> } />
                  {/* OxO v1 term URL (ADR-0004), still linked from outside (GWAS Catalog): the id is a
                      CURIE or OBO short form, redirected to the subject-side search for that term. */}
                  <Route path="/terms/:id" element={<LegacyTermRedirect />} />
                  <Route path="/mapping/:id" element={<MappingDetailsPage /> } />
                  {/* General mapping-set detail surface: the set id is a full IRI carried in ?id=
                      (a path variable would need encoded slashes Tomcat rejects). */}
                  <Route path="/mapping-set" element={<MappingSetPage /> } />
                  {/* Resolvable inferred-set surface (ADR-0012): /inferences = cross-set SSSOM set;
                      /inferences/<encoded source id> = a per-source inferred set. */}
                  <Route path="/inferences" element={<InferencesPage /> } />
                  <Route path="/inferences/*" element={<InferencesPage /> } />
              </Routes>
            </div>
            <Footer />
          </div>
      </QueryClientProvider>
  );
}

export default App;
