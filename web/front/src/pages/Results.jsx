import { Link, useSearchParams } from "react-router-dom";
import SearchUnit from "../components/SearchUnit";
import { useEffect, useState } from "react";
import SearchResults from "../components/SearchResults";
import Pagination from "../components/Pagination";
import Footer from "../components/Footer";
import SettingButton from "../components/SettingButton";
import Settings from "../components/Settings";
import { useSettings } from "../context/SettingsContext";
import falcony_api from "../axiosInstance";
import Loading from "../components/Loading";
// import { Sidebar } from "lucide-react";

import Sidebar from "../components/Sidebar";
import ImageSearchResult from "../components/ImageSearchResult";
import { useQuery } from "../context/QueryContext";

const limit = 10;
const Results = () => {
  const [searchParams] = useSearchParams();
  const [page, setPage] = useState(1); /// i will send the total number of pages to compoent
  const [timeTaken, setTimeTaken] = useState(0); // to be changed to the time taken for the results
  const query = searchParams.get("query");

  const [status, setStatus] = useState("all");

  const { themeColor } = useSettings();
  const [loading, setloading] = useState(false);

  const [totalPages, setTotalPages] = useState(0); // to be changed to the total number of pages

  const [searchResults, setSearchResults] = useState({
    count: 0,
    docs: [],
  });


  const { file } = useQuery();

  useEffect(() => {
    if(!query || query.length === 0) return; // If no query, do not fetch results
    const fetchResults = async () => {
      setloading(true);
      setTimeTaken(0);
      const startTime = Date.now(); // Start time
      try {
        const response = await falcony_api.get(
          `/search?query=${query}&page=${page}&limit=${limit}`
        );
        console.log(response.data);
        setSearchResults(response.data);
        setTotalPages(Math.ceil(response.data.total / limit));
      } catch (error) {
        console.error("Error fetching search results:", error);
      } finally {
        setloading(false);
        // Convert milliseconds to seconds
        setTimeTaken((Date.now() - startTime) / 1000);
      }
    };

    fetchResults();
  }, [query, page]);

  useEffect(() => {
    const fetchImageResults = async () => {
      setloading(true);
      setTimeTaken(0);
      const startTime = Date.now(); // Start time
      try {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("limit", limit*2);

        const response = await falcony_api.post("/search_images", formData);
        
        console.log(response.data);
        setSearchResults(response.data);
        setTotalPages(Math.ceil(response.data.total / limit));
        setStatus("images");
      } catch (error) {
        console.error("Error fetching image search results:", error);
      } finally {
        setloading(false);
        // Convert milliseconds to seconds
        setTimeTaken((Date.now() - startTime) / 1000);
      }
    };

    if (file) {
      fetchImageResults();
    }
  }
  , [file]);

  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  return (
    <>
      <div
        className="  min-h-screen "
        style={{ backgroundColor: themeColor + "20" }}
      >
        <div className="flex items-center justify-center gap-6">
          <Link to="/">
            <img
              src="./src/assets/Falcony_logo.png"
              alt="Logo"
              className="w-[100px] h-[100px]"
            />
          </Link>
          <SearchUnit />
        </div>

        <Sidebar status={status} setStatus={setStatus} />

        <div className=" flex flex-col mt-4 lg:px-[100px] gap-12">
          {loading ? (
            <Loading />
          ) : (
            <>
                <SearchResults results={searchResults} timeTaken={timeTaken} status={status} />
                <ImageSearchResult results={searchResults} status={status}  />
            

              <Pagination
                page={page}
                setPage={setPage}
                totalPages={totalPages}
              />
            </>
          )}
        </div>
      </div>

      <SettingButton
        setIsSettingsOpen={setIsSettingsOpen}
        isSettingsOpen={isSettingsOpen}
      />

      {isSettingsOpen && <Settings setIsSettingsOpen={setIsSettingsOpen} />}

      <Footer />
    </>
  );
};

export default Results;
