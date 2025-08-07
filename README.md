### AI Weather Tool (Spring Boot + Spring AI)

This project is an AI-powered weather assistant built with Spring Boot, Spring AI, and the National Weather Service API (weather.gov). It allows users to query current weather forecasts and active alerts through natural language prompts using OpenAI-style tool calling.

---

### How It Works

- Leverages **Spring AI's `ChatClient`** to process user prompts
- Uses **Tool Calling** to intelligently trigger backend weather functions
- Fetches real-time data from `https://api.weather.gov`
- Supports:
    - Detailed weather forecasts by latitude & longitude
    - Severe weather alerts by U.S. state code

---

### Tech Stack

- Java 17+
- Spring Boot
- Spring AI (`ChatClient`, tool annotations)
- RestClient (Spring 6+)
- National Weather Service API
- Jackson for JSON binding 


---

### How to Run Locally

### 1. Clone the project

```bash
  git clone https://github.com/Rabbi-1/Ai-Weather-Tool.git
  cd AiWeatherTool
```

---

### 2. Set your OpenAI API key

Create a `.env` file or export the variable in your shell:

```
OPENAI_API_KEY=your-openai-api-key
```
The application will read this key using:

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
```

---

### 3. Run the application

Use Maven to start the backend:

```bash
 ./mvnw spring-boot:run
```

---


### Example Output

> Prompt: What’s the weather like in 40.71, -74.00?
>
![img.png](img.png)

> Prompt: Do you know of any weather alerts happening in Texas right now?
> 
> ![img_1.png](img_1.png)