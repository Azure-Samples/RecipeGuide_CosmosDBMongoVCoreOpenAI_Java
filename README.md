# Integrate Open AI Services with Cosmos DB: RAG pattern

This repository provides a demo showcasing the usage of the RAG pattern for integrating Open AI services with custom
data in Cosmos DB. The goal is to limit the responses from Open AI services based on recipes stored in Cosmos DB.

## Prerequisites

- Azure Cosmos DB for MongoDB vCore Account
- Azure Open AI Service
    - Deploy text-davinci-003 model for Embeding
    - Deploy gpt-35-turbo model for Chat Completion

## Dependencies

This demo is built using Java and utilizes the following SDKs:

- MongoDB Driver
- Azure Open AI Services SDK

### Installation

``` bash 
cd OpenAiVCore
mvn compile exec:java
```

## Getting Started

When you run the application for the first time, it will connect to Cosmos DB and report that there are no recipes
available, as we have not uploaded any recipes yet.
To begin, follow these steps:

1) **Upload Documents to Cosmos DB:** Select the first option in the application and hit enter. This option reads
   documents from the local machine and uploads the JSON files to the Cosmos DB NoSQL account.
   #### Parsing files from Local Folder
   ``` Java
    public static List<Recipe> parseDocuments(String directoryPath) {
        List<Recipe> recipes = new ArrayList<>();
        File directory = new File(directoryPath);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    try {
                        Recipe recipe = OBJECT_MAPPER.readValue(file, Recipe.class);
                        recipe.setId(recipe.getName().replace(" ", ""));
                        recipes.add(recipe);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return recipes;
    }
    ```

   #### Upsert Documents to Azure Cosmos DB for MongoDB vCore
    ``` Java
        public void upsertVectors(Recipe recipe) {
            BsonDocument document = recipe.toBsonDocument();
    
            if (!document.containsKey("_id")) {
                log.warn("UpsertVectorAsync: Document does not contain _id.");
            }
    
            String idValue = document.get("_id").asString().getValue();
    
            try {
                var options = new ReplaceOptions();
                options.upsert(true);
                document.remove("_id");
                UpdateResult updateResult = recipeCollection.replaceOne(new BasicDBObject("_id", idValue), document, options);
    
            } catch (Exception ex) {
                log.warn("Exception: UpsertVectorAsync(): ", ex);
            }
    
        }

    ```

2) **Vectorize and Upload Recipes to Azure Cosmos DB for MongoDB vCore:** The JSON data uploaded to Cosmos DB is not yet
   ready for efficient integration with Open AI. To use the RAG pattern, we need to find relevant recipes from Cosmos
   DB. Embeddings help us achieve this. To accomplish the task, we will utilize the vector search capability in Azure
   Cosmos DB for MongoDB vCore to search for embeddings. Firstly, create the required vector search index in Azure
   Cosmos DB for MongoDB vCore. Then, vectorize the recipes and upload the vectors to Azure Cosmos DB for MongoDB vCore.
   Selecting the second option in the application will perform all these activities.

    ####  Build the Vector Index in Azure Cosmos DB for MongoDB vCore
    ``` Java
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
    
    ```

    #### Initialize the Azure Open AI SDK
  	``` Java
    private OpenAIAsyncClient buildAsyncClient() {
        AzureKeyCredential keyCredential = new AzureKeyCredential(AppConfig.openAIKey);
        Duration duration = Duration.of(2L, ChronoUnit.SECONDS);
        RetryOptions retryOptions = new RetryOptions(new FixedDelayOptions(10, duration));
        String endpoint = AppConfig.openAIEndpoint;

        return new OpenAIClientBuilder()
                .retryOptions(retryOptions)
                .endpoint(endpoint)
                .credential(keyCredential)
                .buildAsyncClient();
    }

    ```   
   #### Generate Embedings using Open AI Service
    ``` Java
    public List<Double> getEmbeddings(String data) {
         if (openAIAsyncClient == null) {
             throw new Exception("OpenAiAsyncClient is null");
         }

         EmbeddingsOptions options = new EmbeddingsOptions(List.of(data));

         var response = openAIAsyncClient.getEmbeddings(AppConfig.openAIEmbeddingDeployment, options);

         Embeddings embeddings = response.toFuture().get();

         List<Double> embeddingList = embeddings.getData().get(0).getEmbedding();

         return embeddingList;
    }

    ```
   
3) **Perform Search:** The third option in the application runs the search based on the user query. The user query is converted to an embedding using the Open AI service. The embedding is then sent to Azure Cosmos DB for MongoDB vCore to perform a vector search. The vector search attempts to find vectors that are close to the supplied vector and returns a list of documents from Azure Cosmos DB for MongoDB vCore. The serialized documents retrieved from Azure Cosmos DB for MongoDB vCore are passed as strings to the Open AI service completion service as prompts. During this process, we also include the instructions we want to provide to the Open AI service as prompt. The Open AI service processes the instructions and custom data provided as prompts to generate the response.

    #### Performing Vector Search in Azure Cosmos DB for MongoDB vCore
  	``` Java
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

    ```
   
   #### Prompt Engineering to make sure Open AI service limits the response to supplied recipes
    ``` Java
    private final String systemPromptRecipeAssistant = """
            You are an intelligent assistant for Contoso Recipes.
            You are designed to provide helpful answers to user questions about using
            recipes, cooking instructions only using the provided JSON strings.

            Instructions:
                - In case a recipe is not provided in the prompt politely refuse to answer all queries regarding it.
                - Never refer to a recipe not provided as input to you.
                - If you're unsure of an answer, you can say ""I don't know"" or ""I'm not sure"" and recommend users search themselves.
                - Your response  should be complete.
                - List the Name of the Recipe at the start of your response followed by step by step cooking instructions
                - Assume the user is not an expert in cooking.
                - Format the content so that it can be printed to the Command Line
                - In case there are more than one recipes you find let the user pick the most appropriate recipe.""";

     ```

   #### Generate Chat Completion based on Prompt and Custom Data
    ``` Java
    public ResponseOpenAiData getChatCompletion(String userPrompt, String documents) {
        try {
            ChatMessage systemMessage = new ChatMessage(ChatRole.SYSTEM);
            systemMessage.setContent(systemPromptRecipeAssistant + documents);

            ChatMessage userMessage = new ChatMessage(ChatRole.USER);
            userMessage.setContent(userPrompt);

            List<ChatMessage> chatMessages = List.of(systemMessage, userMessage);

            int maxTokens;
            try {
                maxTokens = AppConfig.openAIMaxToken;
            } catch (Exception e) {
                maxTokens = 8191;
            }

            ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(chatMessages);
            chatCompletionsOptions.setMaxTokens(maxTokens);
            chatCompletionsOptions.setTemperature(0.5d); //0.3d
            chatCompletionsOptions.setFrequencyPenalty(0d);
            chatCompletionsOptions.setPresencePenalty(0d);

            Mono<ChatCompletions> completionsResponse =
                    openAIAsyncClient.getChatCompletions(AppConfig.openAICompletionsDeployment, chatCompletionsOptions);
            ChatCompletions completions = completionsResponse
                    .toFuture().get();

            var responseData = new ResponseOpenAiData();
            responseData.response = completions.getChoices().get(0).getMessage().getContent();
            responseData.promptTokens = completions.getUsage().getPromptTokens();
            responseData.responseTokens = completions.getUsage().getCompletionTokens();

            return responseData;
        } catch (Exception ex) {
            log.warn("Error OpenAIService GetChatCompletion", ex);
        }
        return null;
    }

    ```