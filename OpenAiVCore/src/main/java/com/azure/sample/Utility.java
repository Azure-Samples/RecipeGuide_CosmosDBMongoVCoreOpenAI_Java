package com.azure.sample;

import com.azure.sample.model.Recipe;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.BsonDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Utility {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    public static <T> T jsonDeserialize(BsonDocument document, Class<T> clazz) {
        String jsonString = document.toJson();
        return jsonDeserialize(jsonString, clazz);
    }

    public static <T> T jsonDeserialize(String jsonString, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(jsonString, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String jsonSerialize(Object obj) {

        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
