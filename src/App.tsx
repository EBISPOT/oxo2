import { useRef } from "react";
import { Route, Routes } from "react-router-dom";
import Footer from "./common/Footer";
import Header from "./common/Header";
import About from "./pages/about";
import Documentation from "./pages/documentation";
import EntityView from "./pages/entity";
import Home from "./pages/home";
import MappingView from "./pages/mapping";
import Search from "./pages/search";

function App() {
  const appRef = useRef({ searchQuery: "", mapping: "", entity: "" });
  return (
    <div>
      <Header />
      <Routes>
        <Route path="/" element={<Home appRef={appRef} />} />
        <Route path="/home" element={<Home appRef={appRef} />} />
        <Route path="/search" element={<Search appRef={appRef} />} />
        <Route
          path="/mapping/:mappingId"
          element={<MappingView appRef={appRef} />}
        />
        <Route
          path="/entity/:entityId"
          element={<EntityView appRef={appRef} />}
        />
        <Route path="/docs" element={<Documentation />} />
        <Route path="/about" element={<About />} />
      </Routes>
      <Footer />
    </div>
  );
}

export default App;
