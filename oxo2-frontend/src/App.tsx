import { Route, Routes } from "react-router";

import About from "./pages/about";
import Documentation from "./pages/documentation";
import Footer from "./common/Footer";
import Header from "./common/Header";
import Home from "./pages/home/Home";
import Search from "./pages/search";

function App() {
  return (
      <div>
        <Header />
        <Routes>
          <Route path="/" element={<Home/>} />
          <Route path="/home" element={<Home/>} />
          <Route path="/docs" element={<Documentation />} />
          <Route path="/about" element={<About />} />
          <Route path="/search" element={<Search /> } />
        </Routes>
        <Footer />
      </div>
  );
}


export default App;
