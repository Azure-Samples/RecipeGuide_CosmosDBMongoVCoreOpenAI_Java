// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.sample;

import com.azure.sample.model.Recipe;
import com.azure.sample.service.OpenAIService;
import com.azure.sample.service.VCoreMongoService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Scanner;

@Slf4j
public class VCoreSampleApplication {

    private static final String vectorSearchIndex = "vectorSearchIndex";
    private static OpenAIService openAIEmbeddingService = null;
    private static VCoreMongoService cosmosMongoVCoreService = null;

    public static void main(String[] args) {
        initCosmosDBService();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            log.warn("1.\tUpload recipe(s) to Cosmos DB");
            log.warn("2.\tVectorize the recipe(s) and store it in Cosmos DB\n");
            log.warn("3.\tAsk AI Assistant (search for a recipe by name or description, or ask a question)\n");
            log.warn("4.\tExit this Application");

            String selectedOption = scanner.nextLine();

            switch (selectedOption) {
                case "1":
                    uploadRecipes();
                    break;
                case "2":
                    generateEmbeddings();
                    break;
                case "3":
                    performSearch();
                    break;
                case "4":
                    return;
            }
        }

    }


    private static OpenAIService initOpenAIService() {
        return new OpenAIService();
    }


    private static void initCosmosDBService() {
        long recipeWithEmbedding = 0;
        long recipeWithNoEmbedding = 0;

        log.info("Processing...");

        log.info("Creating Cosmos DB Client...");
        if (initVCoreMongoService()) {
            log.info("Getting Recipe Stats");
            recipeWithEmbedding = cosmosMongoVCoreService.getRecipeCount(true);
            recipeWithNoEmbedding = cosmosMongoVCoreService.getRecipeCount(false);
        }
        var res = String.format("We have %s vectorized recipe(s) and %s non vectorized recipe(s).", recipeWithEmbedding, recipeWithNoEmbedding);
        log.info(res);
    }


    private static boolean initVCoreMongoService() {
        String vcoreConn = AppConfig.mongoVcoreConnection;
        String vCoreDB = AppConfig.mongoVcoreDatabase;
        String vCoreColl = AppConfig.mongoVcoreCollection;
        String maxResults = AppConfig.maxVectorSearchResults;

        if (cosmosMongoVCoreService == null) {
            cosmosMongoVCoreService = new VCoreMongoService(vcoreConn, vCoreDB, vCoreColl, maxResults);
        }
        return true;
    }


    private static void uploadRecipes() {
        String folderPath = "C:\\Users\\v-dchaava\\IdeaProjects\\spring-cloud-azure-vcore-newsample\\recipes";
        long recipeWithEmbedding = 0;
        long recipeWithNoEmbedding = 0;

        log.info("Parsing Recipe files...");

        List<Recipe> recipes = Utility.parseDocuments(folderPath);

        log.info("Uploading Recipe(s)...");
        for (Recipe recipe : recipes) {
            cosmosMongoVCoreService.upsertVectors(recipe);
        }

        log.info("Getting Updated Recipe Stats");
        recipeWithEmbedding = cosmosMongoVCoreService.getRecipeCount(true);
        recipeWithNoEmbedding = cosmosMongoVCoreService.getRecipeCount(false);

        var res = String.format("We have %s vectorized recipe(s) and %s non vectorized recipe(s).", recipeWithEmbedding, recipeWithNoEmbedding);
        log.info(res);
    }


    private static void performSearch() {
        String chatCompletion = "";
        Scanner scanner = new Scanner(System.in);

        log.info("Type the recipe name or your question, hit enter when ready.");
        String userQuery = "baklava";

        scanner.close();

        log.info("Processing...");

        if (openAIEmbeddingService == null) {
            log.info("Connecting to Open AI Service..");
            openAIEmbeddingService = initOpenAIService();
        }


        if (cosmosMongoVCoreService == null) {
            log.info("Connecting to Azure Cosmos DB for MongoDB vCore..");
            initVCoreMongoService();

            log.info("Checking for Index in Azure Cosmos DB for MongoDB vCore..");
            if (!cosmosMongoVCoreService.checkIndexIfExists(vectorSearchIndex)) {
                log.warn("Vector Search Index not Found, Please build the index first.");
                return;
            }
        }

        log.info("Converting User Query to Vector..");
        var embeddingVector = openAIEmbeddingService.getEmbeddings(userQuery);

        log.info("Performing Vector Search from Cosmos DB (RAG pattern)..");
        var retrivedDocs = cosmosMongoVCoreService.vectorSearch(embeddingVector);


        StringBuilder retrivedReceipeNames = new StringBuilder();

        for (var recipe : retrivedDocs) {
            recipe.setEmbedding(null); //removing embedding to reduce tokens during chat completion
            retrivedReceipeNames.append(", ").append(recipe.getName()); //to dispay recipes submitted for Completion
        }

        log.info("Processing retrivedReceipeNames to generate Completion using OpenAI Service..");

        OpenAIService.ResponseOpenAiData data = openAIEmbeddingService.getChatCompletion(userQuery, Utility.jsonSerialize(retrivedDocs));
        chatCompletion = data.response;

        log.info("AI Assistant Response");
        log.info(chatCompletion);
    }


    private static void generateEmbeddings() {
        long recipeWithEmbedding = 0;
        long recipeWithNoEmbedding = 0;
        long recipeCount = 0;

        log.info("Processing...");

        if (openAIEmbeddingService == null) {
            log.info("Connecting to Open AI Service...");
            openAIEmbeddingService = initOpenAIService();
        }

        if (cosmosMongoVCoreService == null) {
            log.info("Connecting to VCore Mongo..");
            initVCoreMongoService();
        }

        log.info("Building VCore Index..");
        cosmosMongoVCoreService.createVectorIndexIfNotExists(vectorSearchIndex);

        log.info("Getting recipe(s) to vectorize..");

        var Recipes = cosmosMongoVCoreService.getRecipesToVectorize();
        for (var recipe : Recipes) {
            recipeCount++;
            var embeddingVector = openAIEmbeddingService.getEmbeddings(Utility.jsonSerialize(recipe));
            recipe.setEmbedding(embeddingVector);
        }

        log.info("Indexing " + Recipes.size() + " document(s) on Azure Cosmos DB for MongoDB vCore..");

        for (var recipe : Recipes) {
            cosmosMongoVCoreService.upsertVectors(recipe);
        }

        recipeWithEmbedding = cosmosMongoVCoreService.getRecipeCount(true);
        recipeWithNoEmbedding = cosmosMongoVCoreService.getRecipeCount(false);

        var res = String.format("Vectorized %s recipe(s). We have %s vectorized recipe(s) and %s non vectorized recipe(s).", recipeCount, recipeWithEmbedding, recipeWithNoEmbedding);
        log.info(res);
    }
}
