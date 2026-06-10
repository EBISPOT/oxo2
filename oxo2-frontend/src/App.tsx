import { Route, Routes } from "react-router";

import About from "./pages/about";
import Documentation from "./pages/documentation";
import Footer from "./components/common/Footer";
import Header from "./components/common/Header";
import Home from "./pages/home/Home";
import MappingResults from "./pages/results/MappingResults";
import MappingDetails from "./pages/results/MappingDetails";
import InferencesPage from "./pages/results/InferencesPage";
import { useLocation } from "react-router-dom";

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient();


function MappingDetailsWrapper() {
    const location = useLocation();
    const mapping = location.state?.mapping;

    return <MappingDetails mapping = {mapping} />;
}

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
                <Route path="/mapping/:id" element={<MappingDetailsWrapper /> } />
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
