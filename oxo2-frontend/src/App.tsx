import { Route, Routes } from "react-router";

import About from "./pages/about";
import Documentation from "./pages/documentation";
import Footer from "./common/Footer";
import Header from "./common/Header";
import Home from "./pages/home/Home";
import MappingResults from "./pages/results/MappingResults";
import { useLocation } from "react-router-dom";
import { SearchInput, initialSearchState } from "./model/Search";
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient();

function SearchResultsWrapper() {
    const location = useLocation();
    const searchInput: SearchInput = location.state?.searchState ?? initialSearchState ;

    return <MappingResults {...searchInput} />;
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
              <Route path="/search" element={<SearchResultsWrapper /> } />
            </Routes>
            <Footer />
          </div>
      </QueryClientProvider>
  );
}

export default App;
