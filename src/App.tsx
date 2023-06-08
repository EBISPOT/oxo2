import { Route, Routes } from "react-router-dom";
import Footer from "./common/Footer";
import Header from "./common/Header";
import About from "./pages/about";
import Documentation from "./pages/documentation";
import Home from "./pages/home";
import MappingView from "./pages/mapping";
import Search from "./pages/search";
import EntityView from "./pages/entity";

function App() {
  return (
    <div>
      <Header />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/home" element={<Home />} />
        <Route path="/search" element={<Search />} />
        <Route path="/mapping/:mappingId" element={<MappingView />} />
        <Route path="/entity/:entityId" element={<EntityView />} />
        <Route path="/docs" element={<Documentation />} />
        <Route path="/about" element={<About />} />
      </Routes>
      <Footer />
    </div>
  );
}

export default App;
