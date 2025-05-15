# Falcony Search Engine

## Features
**The seed we started with is mostly football-related websites**

### 1. Text-Based Search

Falcony provides powerful text search capabilities with support for:
- Standard keyword searching (e.g., "premier league standings")
- Relevance-based result ranking
- PageRank implementation for determining page importance
- Search suggestions based on popular football queries

![Text Search Screenshot Placeholder](#)

### 2. Phrase Searching
Search for exact phrases using quotation marks:
- Example: `"Cristiano Ronaldo goals"`
- Results will include only pages containing the exact phrase

![Phrase Search Screenshot Placeholder](#)

### 3. Boolean Operators
Combine phrases with logical operators for advanced searching:
- AND: `"Lionel Messi" AND "Barcelona"`
- OR: `"Premier League" OR "La Liga"`
- NOT: `"Real Madrid" NOT "Champions League"`

![Boolean Search Screenshot Placeholder](#)

### 4. Image Search
Search by uploading an image to find visually similar football images:
- Uses DinoV2 ONNX model for feature extraction
- Vector similarity search for efficient image matching
- Supports various image formats

![Image Search Screenshot Placeholder](#)

## Architecture

### Backend Components

#### Crawler
- Collects web pages and images from the internet
- Respects robots.txt rules
- Normalizes URLs to avoid duplicates
- Stores documents in MongoDB

#### Indexers
- **TextIndexer**: Processes web page content, tokenizes text, removes stop words, and creates an inverted index
- **ImageIndexer**: Extracts image features using DinoV2 model and stores vector representations

#### Query Processor
- Handles user queries and routes to appropriate rankers
- Supports suggestion generation for autocomplete
- Handles pagination of results

#### Rankers
- **TokenBasedRanker**: Ranks results for keyword searches using TF-IDF and popularity
- **PhraseBasedRanker**: Specialized ranking for phrase searches with boolean operators

#### Database Management
- Uses MongoDB for document and image storage
- Separate collections for documents, tokens, images, and queries
- Vector search capabilities using MongoDB Atlas

### Frontend Components
- React-based user interface
- Real-time search suggestions
- Responsive design for various devices
- Support for both text and image search interfaces

## Technology Stack

- **Backend**: Java
- **Frontend**: React, TailwindCSS
- **Database**: MongoDB
- **Machine Learning**: ONNX Runtime, DinoV2 model
- **Text Processing**: OpenNLP TokenizerME, Porter Stemmer
- **Web Crawling**: JSoup
- **Build Tool**: Gradle

## How It Works

### Text Search Flow
1. User inputs a query like "Champions League final highlights"
2. Query processor analyzes the query to determine if it's a keyword search, phrase search, or boolean search
3. Tokenization and stemming are applied to the query
4. Candidate documents are retrieved from the inverted index
5. Results are ranked based on term frequency, document popularity (PageRank), and other relevance factors
6. Snippets are generated highlighting query terms in context
7. Results are returned to the user interface

### Image Search Flow
1. User uploads an image of a football moment through the interface
2. Image features are extracted using the DinoV2 ONNX model
3. The feature vector is compared against the database of indexed images using vector similarity
4. Similar football images are ranked by cosine similarity
5. Results are returned to the user interface with source documents

### Indexing Process
1. Crawler collects web pages and their images from seed URLs
2. TextIndexer processes textual content:
   - Tokenization and stemming
   - Removal of stop words
   - Creation of inverted index with position information
3. ImageIndexer processes images:
   - Feature extraction with DinoV2
   - Vector normalization
   - Storage in MongoDB with vector indexing

### PageRank Implementation
- Graph representation of web pages and their links
- Iterative calculation of importance scores
- Integration of scores into the document ranking process

## Screenshots

### Home Page
![Home Page Screenshot Placeholder](#)

### Search Results
![Search Results Screenshot Placeholder](#)

### Image Search
![Image Search Screenshot Placeholder](#)

### Search Suggestions
![Search Suggestions Screenshot Placeholder](#)
