package com.azure.sample.service;

import com.azure.sample.Utility;
import com.azure.sample.model.Recipe;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoException;
import com.mongodb.client.*;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.type;

@Slf4j
public class VCoreMongoService {

    private MongoCollection<BsonDocument> recipeCollection;
    private MongoDatabase database;
    private MongoClient client;
    private int maxVectorSearchResults = 10;

    public VCoreMongoService(String connection, String databaseName, String collectionName, String maxVectorSearchResults) {

        if (connection == null || databaseName == null || collectionName == null || maxVectorSearchResults == null) {
            throw new RuntimeException("MongoDb initialization error");
        }

        this.client = MongoClients.create(connection);
        this.database = client.getDatabase(databaseName);
        this.recipeCollection = database.getCollection(collectionName, BsonDocument.class);
        this.maxVectorSearchResults = Integer.parseInt(maxVectorSearchResults);

//        Document doc = new Document("customAction", "CreateCollection")
//                .append("collection", "recipes")
//                .append("shardKey", "_id")
//                .append("offerThroughput", 400);
//
//        Document commandResult = database.runCommand(doc);
//        recipeCollection.deleteMany(new BsonDocument());
    }

    public List<Recipe> getRecipesToVectorize() {
        var mongoCursor = recipeCollection.find().cursor();
        List<BsonDocument> documents = new ArrayList<>();
        while (mongoCursor.hasNext()) {
            documents.add(mongoCursor.next());
        }
        List<Recipe> result = new ArrayList<>(documents.size());
        for (var doc : documents) {
            result.add(Utility.jsonDeserialize(doc, Recipe.class));
        }
        return result;

    }

    public long getRecipeCount(boolean withEmbedding) {
        BsonType type = BsonType.NULL;
        if (withEmbedding) {
            type = BsonType.ARRAY;
        }
        Bson filter = type("embedding", type);

        long count = 0;
        var cursor = recipeCollection.find(filter).cursor();

        while (cursor.hasNext()) {
            count++;
        }

        return count;
    }

    public boolean checkIndexIfExists(String vectorIndexName) {
        try {
            //Find if vector index exists in vectors collection
            try (MongoCursor<Document> indexCursor = recipeCollection.listIndexes().cursor()) {
                return indexCursor.next().toBsonDocument().getArray("name").stream().anyMatch(k -> k.toString().equals(vectorIndexName));
            }
        } catch (MongoException ex) {
            log.warn("MongoDbService InitializeVectorIndex: ", ex);
        }
        return false;
    }

    public void createVectorIndexIfNotExists(String vectorIndexName) {

        try {
            //Find if vector index exists in vectors collection
            try (MongoCursor<Document> indexCursor = recipeCollection.listIndexes().cursor()) {
                boolean vectorIndexExists = false;
                while (indexCursor.hasNext()) {
                    BsonDocument bsonDocument = indexCursor.next().toBsonDocument();
                    if (bsonDocument.get("name").equals(vectorIndexName)) {
                        vectorIndexExists = true;
                        break;
                    }
                }

                if (!vectorIndexExists) {
                    Bson bsonCommand = BsonDocument.parse(
                            """
                                    { createIndexes: 'recipes',
                                      indexes: [{
                                        name: 'vectorSearchIndex',
                                        key: { embedding: \"cosmosSearch\" },
                                        cosmosSearchOptions: { kind: 'vector-ivf', numLists: 5, similarity: 'COS', dimensions: 1536 }\s
                                      }]
                                    }
                                    """);

                    Document result = database.runCommand(bsonCommand);
                    if (!result.containsKey("ok")) {
                        log.warn("CreateIndex failed with response: " + result.toJson());
                    }
                }
            }

        } catch (MongoException ex) {
            log.warn("MongoDbService InitializeVectorIndex: ", ex);
        }

    }

    public List<Recipe> vectorSearch(List<Double> queryVector) {
        List<String> retDocs = new ArrayList<>();
        String resultDocuments = "";
        try {
            //Search Azure Cosmos DB for MongoDB vCore collection for similar embeddings
            //Project the fields that are needed
            String joined = queryVector.stream().map(String::valueOf)
                    .collect(Collectors.joining(","));
            String formatted = String.format("{$search: {cosmosSearch: { vector: [%s], " +
                    "path: 'embedding', k: %d}, returnStoredSource:true}}", joined, maxVectorSearchResults);

            List<BsonDocument> pipeline = List.of(
                    BsonDocument.parse(formatted),
                    BsonDocument.parse("{$project: {embedding: 0}}"));

            var recipeCursor = recipeCollection.aggregate(pipeline, BsonDocument.class).cursor();

            List<BsonDocument> bsonDocuments = new ArrayList<>();

            while (recipeCursor.hasNext()) {
                bsonDocuments.add(recipeCursor.next());
            }
            //deserialize to <Recipe>
            List<Recipe> result = new ArrayList<>(bsonDocuments.size());
            for (var doc : bsonDocuments) {
                result.add(Utility.jsonDeserialize(doc, Recipe.class));
            }
            return result;
        } catch (MongoException ex) {
            log.warn("Exception: VectorSearch(): ", ex);
        }
        log.warn("VectorSearch error");
        return null;
    }

    public void upsertVectors(Recipe recipe) {
        BsonDocument document = recipe.toBsonDocument();

        if (!document.containsKey("_id")) {
            log.warn("UpsertVectorAsync: Document does not contain _id.");
        }

        String idValue = document.get("_id").asString().getValue();

        try {
//            var filter = eq("_id", idValue);
            var options = new ReplaceOptions();
            options.upsert(true);
            document.remove("_id");
            UpdateResult updateResult = recipeCollection.replaceOne(new BasicDBObject("_id", idValue), document, options);

        } catch (Exception ex) {
            log.warn("Exception: UpsertVectorAsync(): ", ex.getMessage());
        }

    }
}
