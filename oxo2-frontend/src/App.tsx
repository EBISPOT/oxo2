import { Route, Routes } from "react-router";

import About from "./pages/about";
import Documentation from "./pages/documentation";
import Footer from "./components/common/Footer";
import Header from "./components/common/Header";
import Home from "./pages/home/Home";
import MappingResults from "./pages/results/MappingResults";
import MappingDetailsPage from "./pages/results/MappingDetailsPage";
import MappingSetPage from "./pages/results/MappingSetPage";
import InferencesPage from "./pages/results/InferencesPage";

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient();

function App() {

  return (
      <QueryClientProvider client={queryClient}>
          <div>
            <Header />
            <Routes>
                <Route path="/" element={<Home/>} />
                <Route path="/home" element={<Home/>} />
                <Route path="/docs" element={<Documentation />} />
                <Route path="/about" element={<About />} />
                <Route path="/search/:curies" element={<MappingResults /> } />
                <Route path="/mapping/:id" element={<MappingDetailsPage /> } />
                {/* General mapping-set detail surface: the set id is a full IRI carried in ?id=
                    (a path variable would need encoded slashes Tomcat rejects). */}
                <Route path="/mapping-set" element={<MappingSetPage /> } />
                {/* Resolvable inferred-set surface (ADR-0012): /inferences = cross-set SSSOM set;
                    /inferences/<encoded source id> = a per-source inferred set. */}
                <Route path="/inferences" element={<InferencesPage /> } />
                <Route path="/inferences/*" element={<InferencesPage /> } />
            </Routes>
            <Footer />
          </div>
      </QueryClientProvider>
  );
}

export default App;
