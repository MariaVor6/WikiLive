using Microsoft.AspNetCore.Mvc;
using System.Net.Http.Headers;
using System.Text.Json;

namespace WikiLive.Api.Controllers
{
    // КОНТРОЛЛЕР-ПРОКСИ: Общается с реальным API MWS Tables
    // Фронтенд шлет запросы сюда, а мы пересылаем их во Fusion API с авторизацией
    [Route("api/[controller]")]
    [ApiController]
    public class MwsProxyController : ControllerBase
    {
        private readonly HttpClient _httpClient;
        // Базовый URL платформы MWS Tables
        private const string MwsBaseUrl = "https://fusion.mws.ru"; 

        public MwsProxyController(IHttpClientFactory httpClientFactory)
        {
            _httpClient = httpClientFactory.CreateClient();
        }

        // Метод для добавления токена из запроса фронтенда в запрос к MWS
        private void SetAuthHeader()
        {
            if (Request.Headers.TryGetValue("Authorization", out var authHeader))
            {
                _httpClient.DefaultRequestHeaders.Authorization = AuthenticationHeaderValue.Parse(authHeader!);
            }
            else
            {
                // Если токен не пришел, используем заглушку для теста (замени на реальный usk-токен)
                _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "usk-test-token");
            }
        }

        // Получить все пространства пользователя
        [HttpGet("spaces")]
        public async Task<IActionResult> GetSpaces()
        {
            SetAuthHeader();
            var response = await _httpClient.GetAsync($"{MwsBaseUrl}/fusion/v1/spaces");
            return await HandleResponse(response);
        }

        // Получить таблицы (ноды) внутри пространства
        [HttpGet("spaces/{spaceId}/nodes")]
        public async Task<IActionResult> GetNodes(string spaceId, [FromQuery] int? type)
        {
            SetAuthHeader();
            var url = $"{MwsBaseUrl}/fusion/v1/spaces/{spaceId}/nodes";
            if (type.HasValue) url += $"?type={type}";
            
            var response = await _httpClient.GetAsync(url);
            return await HandleResponse(response);
        }

        // Получить структуру колонок таблицы
        [HttpGet("datasheets/{dstId}/fields")]
        public async Task<IActionResult> GetFields(string dstId)
        {
            SetAuthHeader();
            var response = await _httpClient.GetAsync($"{MwsBaseUrl}/fusion/v1/datasheets/{dstId}/fields");
            return await HandleResponse(response);
        }

        // Получить строки с данными из таблицы
        [HttpGet("datasheets/{dstId}/records")]
        public async Task<IActionResult> GetRecords(string dstId)
        {
            SetAuthHeader();
            var response = await _httpClient.GetAsync($"{MwsBaseUrl}/fusion/v1/datasheets/{dstId}/records");
            return await HandleResponse(response);
        }

        // Обновить ячейки в таблице (PATCH запрос в MWS)
        [HttpPatch("datasheets/{dstId}/records")]
        public async Task<IActionResult> UpdateRecords(string dstId, [FromBody] JsonElement data)
        {
            SetAuthHeader();
            var response = await _httpClient.PatchAsJsonAsync($"{MwsBaseUrl}/fusion/v1/datasheets/{dstId}/records", data);
            return await HandleResponse(response);
        }

        // Универсальный обработчик ответов от MWS
        private async Task<IActionResult> HandleResponse(HttpResponseMessage response)
        {
            var content = await response.Content.ReadAsStringAsync();
            if (response.IsSuccessStatusCode)
            {
                // Если запрос успешен, возвращаем JSON как есть
                return Ok(JsonSerializer.Deserialize<JsonElement>(content));
            }
            // Если ошибка (например 401 или 404), пробрасываем её код и текст
            return StatusCode((int)response.StatusCode, content);
        }
    }
}