import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class Main {
    public static final String API_KEY = "484f4e63198cc39f9e9c72d002f9269d";

    // API Callout base urls
    public static final String TMDB_DISCOVER_URL = "https://api.themoviedb.org/3/discover/movie?api_key="+ API_KEY + "&";
    public static final String TMDB_SEARCH_ACTOR_URL = "http://api.tmdb.org/3/search/person?api_key=" + API_KEY + "&query=";
    public static final String TMDB_SEARCH_GENRE_URL = "http://api.tmdb.org/3/genre/movie/list?api_key=" + API_KEY;

    // Parameters for urls
    public static final String WITH_CAST = "with_cast";
    public static final String WITH_GENRES = "with_genres";
    public static final String VOTE_AVERAGE = "vote_average.gte";
    public static final String YEAR = "year";

    // Mapping of actors and genres with ids
    private static HashMap<Integer, String> IdGenreMap = new HashMap<Integer, String>();
    private static HashMap<Integer, String> IdActorMap = new HashMap<Integer, String>();

    /**
     * Main method
     * Goal:
     * 1) get user input for list of actors, list of genres, imdb rating, and year of movie
     * 2) Connect with TMDB API to return response to user's input
     * 3) ---- MAYBE Use Beautiful Soup to parse Cinplex website for possible showtimes
     * 4) ---- MAYBE Send user text message of showtimes (if any)
     * 5) Send user text message of response to user's initial input using Twilio API
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            // these params will actually come from the params to main
            populateIdGenreMap();
            List<String> actorList = new ArrayList<String>();
            List<String> genreList = new ArrayList<String>();

            Number imdbRating;
            Integer year;

            // mock data
            actorList.add("Kevin Hart");
            actorList.add("Dwayne Johnson");
            //actorList.add("Rebel Wilson");

            //genreList.add("Action");

            imdbRating = null;
            year = null;

            List<Movie> movieList = linkTMDBWithUnirest(actorList, genreList, imdbRating, year);

            sendText(movieList);

        } catch (UnirestException e) {
            e.printStackTrace();
            System.out.println("RAVINA: UNIREST EXCEPTION THROWN :(");
        }
    }

    private static void sendText(List<Movie> movieList) {
        String text = "Hello! Your recent movie discovery with actor(s) ";

        String csActors = "";
        for(String actor : movieList.get(0).actorList) {
            if(csActors.length() > 0) csActors += ", ";
            csActors += actor;
        }

        text += csActors + " has produced the following results: \n \n";


        for(Movie movie : movieList) {
            text += "TITLE: " + movie.title + "\n";
            text += "OVERVIEW: " + movie.overview + "\n";

            String csGenres = "";
            for(String genre : movie.genreList) {
                if(csGenres.length() > 0) csGenres += ", ";
                csGenres += genre;
            }

            text += "GENRES(S): " + csGenres + "\n";
            text += "RATING: " + movie.rating + "\n";
            text += "RELEASE DATE: " + movie.releaseDate + "\n";

            text += "\n \n";
        }

        // in case text is > 1600 characters, send multiple messages
        System.out.println("RAVINA: text.length() = " + text.length());
        if(text.length() < 1500) SmsSender.sendSMS("+17788962742", text);
        else {
            SmsSender.sendSMS("+17788962742", text.substring(0, 1500));
        }


        System.out.println("Ravina: text = " + text);

    }

    /**
     * Method to connect to API
     * @param actorList List<String> actors to send to API
     * @param genreList List<String> genres to send to API
     * @param imdbRating Number  imdb rating to send to API
     * @param year Integer  year of movie to send to API
     *
     * @returns List<Movie> list of extracted movies
     * @throws UnirestException
     */
    public static List<Movie> linkTMDBWithUnirest(List<String> actorList, List<String> genreList, Number imdbRating, Integer year) throws UnirestException {

        String csActors = getActorIds(actorList);
        String csGenres = getGenreIds(genreList);

        // form url for unirest
        String parametersForUrl = formUrlParams(csActors, csGenres, imdbRating, year);

        HttpResponse<JsonNode> body = makeApiCallout(TMDB_DISCOVER_URL + parametersForUrl);

        int statusCode = body.getStatus();
        JsonNode responseBody = body.getBody();

        System.out.println("ravina: statusCode = " + statusCode);
        System.out.println("ravina: responseBody = " + responseBody);

        // extract movies out of response
        List<Movie> movieList = extractMovies(responseBody, actorList);
        System.out.println("Ravina: movieList = " + movieList);
//        for(Movie movie : movieList) {
//            System.out.println("RAVINA: movie title = " + movie.title);
//        }

        return movieList;



    }

    /**
     * Method to parse TMDB response into a list of movies with its details
     * @param responseBody TMDB response to parse
     * @param actorList list of actors that the user wants included in the movies
     * @return List<Movie>  list of movies parsed from responseBody
     */
    private static List<Movie> extractMovies(JsonNode responseBody, List<String> actorList) {
        List<Movie> movieList = new ArrayList<Movie>();

        JSONObject responseObject = responseBody.getObject();
        JSONArray results = responseObject.getJSONArray("results");

        for(int i = 0; i < results.length(); i++) {
            JSONObject movieObject = results.getJSONObject(i);
            String title = movieObject.getString("title");

            // get genre list
            JSONArray genreIdArray = movieObject.getJSONArray("genre_ids");
            List<String> genreList = new ArrayList<String>();
            for(int j = 0; j < genreIdArray.length(); j++) {
                Integer genreId = genreIdArray.getInt(j);
                if(IdGenreMap.get(genreId) != null) genreList.add(IdGenreMap.get(genreId));
            }

            Number rating = (Number) movieObject.get("vote_average");
            String overview = movieObject.getString("overview");
            String releaseDate = movieObject.getString("release_date");

            Movie movie = new Movie(title, genreList, actorList, rating, overview, releaseDate);
            System.out.println("RAVINA: movie = " + movie.title + " " + movie.genreList + " " + movie.actorList + " " + movie.rating + " " + movie.overview + " " + movie.releaseDate);
            movieList.add(movie);
        }

        for(Movie movie : movieList) {
            System.out.println("RAVINA: movie title = " + movie.title);
        }


        return movieList;
    }

    /**
     * Method to make API Callout with Unirest given url
     * @param url url to make API callout with
     * @return HttpResponse<JsonNode> response of API callout
     * @throws UnirestException
     */
    private static HttpResponse<JsonNode> makeApiCallout(String url) throws UnirestException {
        HttpResponse<JsonNode> body = Unirest.get(url)
                .header("accept", "application/json")
                .asJson();

        return body;
    }

    /**
     * Method to the parameters of the url
     * @param csActors Comma-separated String of actor ids to put in WITH_CAST param
     * @param csGenres Comma-separated String of genre ids to put in WITH _GENRES param
     * @param imdbRating Number  rating to put into VOTE_AVERAGE param
     * @param year Integer  year to put into YEAR param
     *
     * @return String substring of params that will eventually be appended to a base url
     */
    private static String formUrlParams(String csActors, String csGenres, Number imdbRating, Integer year) {
        String parametersForUrl = "";
        if(csActors != null && csActors.length() > 0) {
            parametersForUrl += WITH_CAST + "=" + csActors;
        }

        if(csGenres != null && csGenres.length() > 0) {
            if(parametersForUrl.length() != 0) parametersForUrl+= "&";
            parametersForUrl += WITH_GENRES + "=" + csGenres;
        }

        if(imdbRating != null) {
            if(parametersForUrl.length() != 0) parametersForUrl+= "&";
            parametersForUrl += VOTE_AVERAGE + "=" + imdbRating;
        }

        if(year != null) {
            if(parametersForUrl.length() != 0) parametersForUrl+= "&";
            parametersForUrl += YEAR + "=" + year;
        }

        System.out.println("RAVINA: parametersForUrl = " + parametersForUrl);
        return parametersForUrl;
    }


    /**
     * Method to get all the genre ids in genreList
     *
     * @param genreList List<String> genres whose ids should be obtained
     *
     * @return String comma-separated string of genre ids
     *
     * @throws UnirestException
     */
    private static String getGenreIds(List<String> genreList) throws UnirestException {
        String csGenres = "";

        HttpResponse<JsonNode> body = makeApiCallout(TMDB_SEARCH_GENRE_URL);
        JsonNode responseBody = body.getBody();
        JSONObject jsonObject = responseBody.getObject();

        HashMap<String, Integer> nameIdMap = new HashMap<String, Integer>();

        JSONArray genreArray = jsonObject.getJSONArray("genres");

        for(int i = 0; i < genreArray.length(); i++)
        {
            JSONObject object = genreArray.getJSONObject(i);
            nameIdMap.put((String) object.get("name"), (Integer) object.get("id"));
        }

        for(String genre : genreList) {
            if(nameIdMap.containsKey(genre)) {
                if(csGenres.length() > 0) csGenres += ",";
                csGenres += nameIdMap.get(genre);
            }
        }

        System.out.println("RAVINA: csGenres = " + csGenres);
        return csGenres;
    }

    /**
     * Method to get all the actors ids in actorList
     *
     * @param actorList List<String> actors whose ids should be obtained
     *
     * @return String comma-separated String of actor ids
     *
     * @throws UnirestException
     */
    private static String getActorIds(List<String> actorList) throws UnirestException {
        String csActors = "";

        for(String actor : actorList) {
            // make api callout, get ids of actors and append to csActors
            HttpResponse<JsonNode> body = null;

            String[] splitName = actor.split("\\s+");
            if(splitName != null && splitName.length == 2) {
                String firstName = splitName[0];
                String lastName = splitName[1];

                body = makeApiCallout(TMDB_SEARCH_ACTOR_URL + firstName + "+" + lastName);
            }

            if(body != null) {
                JsonNode responseBody = body.getBody();
                JSONObject jsonObject = responseBody.getObject();
                JSONArray results = jsonObject.getJSONArray("results");
                if(results != null) {
                    JSONObject actorObject = results.getJSONObject(0);
                    Integer id = (Integer) actorObject.get("id");

                    if(id != null) {
                        if(csActors.length() > 0) csActors += ",";
                        csActors += id;

                        IdActorMap.put(id, actor);
                    }
                }
            }
        }

        System.out.println("RAVINA: csActors = " + csActors);
        return csActors;
    }

    /**
     * Method to get all genres available on IMDB and map Id to Genre name
     * @throws UnirestException
     */
    private static void populateIdGenreMap() throws UnirestException {

        HttpResponse<JsonNode> body = makeApiCallout(TMDB_SEARCH_GENRE_URL);
        JsonNode responseBody = body.getBody();
        JSONObject jsonObject = responseBody.getObject();

        JSONArray genreArray = jsonObject.getJSONArray("genres");

        for(int i = 0; i < genreArray.length(); i++)
        {
            JSONObject object = genreArray.getJSONObject(i);
            IdGenreMap.put((Integer) object.get("id"), (String) object.get("name"));
        }

        System.out.println("RAVINA: IdGenreMap = " + IdGenreMap);
    }

}
