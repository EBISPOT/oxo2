import { Route, Routes } from "react-router-dom";
import About from "./pages/about";
import Documentation from "./pages/documentation";
import Home from "./pages/home";

function App() {
  return (
    <Routes>
      <Route path={`/`} element={<Home />} />
      <Route path="/home" element={<Home />} />
      <Route path="/docs" element={<Documentation />} />
      <Route path="/about" element={<About />} />
    </Routes>
  );
}

export default App;
