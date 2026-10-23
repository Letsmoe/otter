package tmdb

// TMDB v3 API response shapes, only the fields otter uses. Detail requests
// append credits, videos, images, keywords and recommendations in one call.

type SearchResult struct {
	ID           int     `json:"id"`
	MediaType    string  `json:"media_type"` // set by multi-search and trending only
	Title        string  `json:"title"`
	Name         string  `json:"name"`
	ReleaseDate  string  `json:"release_date"`
	FirstAirDate string  `json:"first_air_date"`
	PosterPath   string  `json:"poster_path"`
	Overview     string  `json:"overview"`
	VoteAverage  float64 `json:"vote_average"`
	VoteCount    int     `json:"vote_count"`
	Popularity   float64 `json:"popularity"`
	BackdropPath string  `json:"backdrop_path"`
	GenreIDs     []int   `json:"genre_ids"`
	Adult        bool    `json:"adult"`
}

type SearchResponse struct {
	Results    []SearchResult `json:"results"`
	TotalPages int            `json:"total_pages"`
}

type Genre struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

type CastMember struct {
	ID          int     `json:"id"`
	Name        string  `json:"name"`
	Character   string  `json:"character"`
	Order       int     `json:"order"`
	Gender      int     `json:"gender"`
	Popularity  float64 `json:"popularity"`
	ProfilePath string  `json:"profile_path"`
	KnownFor    string  `json:"known_for_department"`
	Adult       bool    `json:"adult"`
	CreditID    string  `json:"credit_id"`
	CastID      int     `json:"cast_id"`
}

type CrewMember struct {
	ID          int     `json:"id"`
	Name        string  `json:"name"`
	Job         string  `json:"job"`
	Department  string  `json:"department"`
	Gender      int     `json:"gender"`
	Popularity  float64 `json:"popularity"`
	ProfilePath string  `json:"profile_path"`
	Adult       bool    `json:"adult"`
	CreditID    string  `json:"credit_id"`
}

type Credits struct {
	Cast []CastMember `json:"cast"`
	Crew []CrewMember `json:"crew"`
}

type Video struct {
	Name        string `json:"name"`
	Site        string `json:"site"`
	Key         string `json:"key"`
	Type        string `json:"type"`
	Official    bool   `json:"official"`
	PublishedAt string `json:"published_at"`
	Size        int    `json:"size"`
	ISO6391     string `json:"iso_639_1"`
	ISO31661    string `json:"iso_3166_1"`
}

type Videos struct {
	Results []Video `json:"results"`
}

type Image struct {
	FilePath    string  `json:"file_path"`
	Width       int     `json:"width"`
	Height      int     `json:"height"`
	AspectRatio float64 `json:"aspect_ratio"`
	VoteAverage float64 `json:"vote_average"`
	VoteCount   int     `json:"vote_count"`
	ISO6391     string  `json:"iso_639_1"`
}

type Images struct {
	Posters   []Image `json:"posters"`
	Backdrops []Image `json:"backdrops"`
	Logos     []Image `json:"logos"`
}

type MovieDetail struct {
	ID               int     `json:"id"`
	IMDBID           string  `json:"imdb_id"`
	Title            string  `json:"title"`
	OriginalTitle    string  `json:"original_title"`
	OriginalLanguage string  `json:"original_language"`
	Tagline          string  `json:"tagline"`
	Overview         string  `json:"overview"`
	Status           string  `json:"status"`
	ReleaseDate      string  `json:"release_date"`
	Runtime          int     `json:"runtime"`
	Budget           int     `json:"budget"`
	Revenue          int     `json:"revenue"`
	Popularity       float64 `json:"popularity"`
	VoteAverage      float64 `json:"vote_average"`
	VoteCount        int     `json:"vote_count"`
	Homepage         string  `json:"homepage"`
	PosterPath       string  `json:"poster_path"`
	BackdropPath     string  `json:"backdrop_path"`
	Adult            bool    `json:"adult"`
	Video            bool    `json:"video"`
	Genres           []Genre `json:"genres"`

	Credits         Credits        `json:"credits"`
	Videos          Videos         `json:"videos"`
	Images          Images         `json:"images"`
	Keywords        Keywords       `json:"keywords"`
	Recommendations SearchResponse `json:"recommendations"`
}

// Keywords covers both TMDB shapes: movies list them under "keywords",
// series under "results".
type Keywords struct {
	Keywords []Genre `json:"keywords"`
	Results  []Genre `json:"results"`
}

// Names returns the keyword names of either shape.
func (k Keywords) Names() []string {
	names := make([]string, 0, len(k.Keywords)+len(k.Results))
	for _, keyword := range k.Keywords {
		names = append(names, keyword.Name)
	}
	for _, keyword := range k.Results {
		names = append(names, keyword.Name)
	}
	return names
}

type ExternalIDs struct {
	IMDBID string `json:"imdb_id"`
}

// SeasonSummary is a season entry as it appears in a series' `seasons` array
// (GET /tv/{id}). Used to create the canonical season rows.
type SeasonSummary struct {
	ID           int     `json:"id"`
	SeasonNumber int     `json:"season_number"`
	Name         string  `json:"name"`
	Overview     string  `json:"overview"`
	AirDate      string  `json:"air_date"`
	PosterPath   string  `json:"poster_path"`
	EpisodeCount int     `json:"episode_count"`
	VoteAverage  float64 `json:"vote_average"`
}

// EpisodeSummary is an episode entry as it appears in a season's `episodes`
// array (GET /tv/{id}/season/{n}). Used to create the canonical episode rows.
type EpisodeSummary struct {
	ID             int     `json:"id"`
	EpisodeNumber  int     `json:"episode_number"`
	SeasonNumber   int     `json:"season_number"`
	Name           string  `json:"name"`
	Overview       string  `json:"overview"`
	AirDate        string  `json:"air_date"`
	Runtime        int     `json:"runtime"`
	StillPath      string  `json:"still_path"`
	ProductionCode string  `json:"production_code"`
	VoteAverage    float64 `json:"vote_average"`
	VoteCount      int     `json:"vote_count"`
}

type SeasonDetail struct {
	ID           int     `json:"id"`
	Name         string  `json:"name"`
	Overview     string  `json:"overview"`
	AirDate      string  `json:"air_date"`
	SeasonNumber int     `json:"season_number"`
	PosterPath   string  `json:"poster_path"`
	VoteAverage  float64 `json:"vote_average"`

	Episodes []EpisodeSummary `json:"episodes"`
}

type SeriesDetail struct {
	ID               int     `json:"id"`
	Name             string  `json:"name"`
	OriginalName     string  `json:"original_name"`
	OriginalLanguage string  `json:"original_language"`
	Tagline          string  `json:"tagline"`
	Overview         string  `json:"overview"`
	Status           string  `json:"status"`
	FirstAirDate     string  `json:"first_air_date"`
	LastAirDate      string  `json:"last_air_date"`
	NumberOfSeasons  int     `json:"number_of_seasons"`
	NumberOfEpisodes int     `json:"number_of_episodes"`
	Popularity       float64 `json:"popularity"`
	VoteAverage      float64 `json:"vote_average"`
	VoteCount        int     `json:"vote_count"`
	Homepage         string  `json:"homepage"`
	PosterPath       string  `json:"poster_path"`
	BackdropPath     string  `json:"backdrop_path"`
	Adult            bool    `json:"adult"`
	Genres           []Genre `json:"genres"`

	Seasons []SeasonSummary `json:"seasons"`

	Credits         Credits        `json:"credits"`
	Videos          Videos         `json:"videos"`
	Images          Images         `json:"images"`
	Keywords        Keywords       `json:"keywords"`
	Recommendations SearchResponse `json:"recommendations"`
	ExternalIDs     ExternalIDs    `json:"external_ids"`
}
