package com.azure.sample.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;

@Data
public class Recipe {
    @BsonId
    @JsonProperty("_id")
    private String id;

    private String name;
    private String description;
    private String cuisine;
    private String difficulty;
    private String prepTime;
    private String cookTime;
    private String totalTime;
    private int servings;
    private List<Double> embedding;
    private List<String> ingredients;
    private List<String> instructions;

    public BsonDocument toBsonDocument() {
        BsonDocument document = new BsonDocument();
        document.put("_id", new BsonString(id));
        document.put("_name", new BsonString(name));
        document.put("_description", new BsonString(description));
        document.put("_cuisine", new BsonString(cuisine));
        document.put("_difficulty", new BsonString(difficulty));
        document.put("_prepTime", new BsonString(prepTime));
        document.put("_cookTime", new BsonString(cookTime));
        document.put("_totalTime", new BsonString(totalTime));
        return document;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCuisine() {
        return cuisine;
    }

    public void setCuisine(String cuisine) {
        this.cuisine = cuisine;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getPrepTime() {
        return prepTime;
    }

    public void setPrepTime(String prepTime) {
        this.prepTime = prepTime;
    }

    public String getCookTime() {
        return cookTime;
    }

    public void setCookTime(String cookTime) {
        this.cookTime = cookTime;
    }

    public String getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(String totalTime) {
        this.totalTime = totalTime;
    }

    public int getServings() {
        return servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public List<String> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<String> ingredients) {
        this.ingredients = ingredients;
    }

    public List<String> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<String> instructions) {
        this.instructions = instructions;
    }
}
