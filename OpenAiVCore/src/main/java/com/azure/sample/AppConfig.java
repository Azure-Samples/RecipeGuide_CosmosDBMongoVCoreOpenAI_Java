package com.azure.sample;

import org.apache.commons.lang3.StringUtils;

public class AppConfig {
    public static String recipeLocalFolder = System.getProperty("RECIPE_LOCAL_FOLDER",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("RECIPE_LOCAL_FOLDER")),
                    "<RECIPE_LOCAL_FOLDER>"));
    public static String openAIEndpoint = System.getProperty("OPENAI_ENDPOINT",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("OPENAI_ENDPOINT")),
                    "<OPENAI_ENDPOINT>"));
    public static String openAIKey = System.getProperty("OPENAI_KEY",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("OPENAI_KEY")),
                    "<OPENAI_KEY>"));
    public static String openAIEmbeddingDeployment = System.getProperty("OPENAI_EMBEDDING_DEPLOYMENT",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("OPENAI_EMBEDDING_DEPLOYMENT")),
                    "<OPENAI_EMBEDDING_DEPLOYMENT>"));
    public static String openAICompletionsDeployment = System.getProperty("OPENAI_COMPLETIONS_DEPLOYMENT",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("OPENAI_COMPLETIONS_DEPLOYMENT")),
                    "<OPENAI_COMPLETIONS_DEPLOYMENT>"));
    public static int openAIMaxToken = Integer.parseInt(System.getProperty("OPENAI_MAX_TOKEN",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("OPENAI_MAX_TOKEN")),
                    "64")));
    public static String mongoVcoreConnection = System.getProperty("MONGO_VCORE_CONNECTION",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("MONGO_VCORE_CONNECTION")),
                    "<MONGO_VCORE_CONNECTION>"));

    public static String mongoVcoreDatabase = System.getProperty("MONGO_VCORE_DATABASE",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("MONGO_VCORE_DATABASE")),
                    "<MONGO_VCORE_DATABASE>"));

    public static String mongoVcoreCollection = System.getProperty("MONGO_VCORE_COLLECTION",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("MONGO_VCORE_COLLECTION")),
                    "<MONGO_VCORE_COLLECTION>"));

    public static String maxVectorSearchResults = System.getProperty("MAX_VECTOR_SEARCH_RESULTS",
            StringUtils.defaultString(StringUtils.trimToNull(
                            System.getenv().get("MAX_VECTOR_SEARCH_RESULTS")),
                    "234324"));


}
