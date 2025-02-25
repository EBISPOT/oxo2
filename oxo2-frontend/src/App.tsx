import { useRef } from "react";
import { Route, Routes } from "react-router";

import About from "./pages/about";
import Documentation from "./pages/documentation";
import Footer from "./common/Footer";
import Header from "./common/Header";
import Home from "./pages/home";
import Search from "./pages/search";

function App() {
  const appRef = useRef({ searchQuery: ""});
  return (
      <div>
        <Header />
        <Routes>
          <Route path="/" element={<Home appRef={appRef} />} />
          <Route path="/home" element={<Home appRef={appRef} />} />
          <Route path="/docs" element={<Documentation />} />
          <Route path="/about" element={<About />} />
          <Route path="/search" element={<Search appRef={appRef}/>} />
        </Routes>
        <Footer />
      </div>
  );
}


export default App;
