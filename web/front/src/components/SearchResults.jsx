import React from "react";
import { Search, ExternalLink } from "lucide-react";
import SearchItem from "./SearchItem";

function SearchResults({ results = {}, timeTaken, status }) {
  console.log("SearchResults", results, timeTaken);
  if(status !== "all") return;
  return (
    <div className="flex flex-col  w-full px-4 py-6 ">
      {/* Results Count */}
      <p className="text-sm text-gray-500 mb-4">
        About {results?.total} results in {timeTaken} seconds
      </p>

      {/* Results List */}
      <div className="w-full space-y-6">
        {results?.docs?.map((item, index) => (
          <SearchItem key={index} item={item} />
        ))}
      </div>
    </div>
  );
}

export default SearchResults;
