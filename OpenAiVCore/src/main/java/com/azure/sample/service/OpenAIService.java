package com.azure.sample.service;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.FixedDelayOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.sample.AppConfig;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
public class OpenAIService {
    private OpenAIAsyncClient openAIAsyncClient = null;

    //System prompts to send with user prompts to instruct the model for chat session
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

    public OpenAIService() {
        this.openAIAsyncClient = buildAsyncClient();
    }

    public List<Double> getEmbeddings(String data) {
        try {
            if (openAIAsyncClient == null) {
                throw new Exception("OpenAiAsyncClient is null");
            }

            EmbeddingsOptions options = new EmbeddingsOptions(List.of(data));

            var response = openAIAsyncClient.getEmbeddings(AppConfig.openAIEmbeddingDeployment, options);

            Embeddings embeddings = response.toFuture().get();

            List<Double> embeddingList = embeddings.getData().get(0).getEmbedding();

            return embeddingList;
        } catch (Exception ex) {
            log.warn("GetEmbeddings Exception: " + ex.getMessage());
            return null;
        }
    }

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
            log.warn("Error OpenAIService GetChatCompletionAsync", ex);
        }
        return null;
    }

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

    public static final class ResponseOpenAiData {
        public String response;
        public int promptTokens;
        public int responseTokens;
    }
}
