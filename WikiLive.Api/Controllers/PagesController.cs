using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WikiLive.Api.Data;
using WikiLive.Api.Models;

namespace WikiLive.Api.Controllers
{
    // Контроллер для работы со страницами Вики
    [Route("api/[controller]")]
    [ApiController]
    public class PagesController : ControllerBase
    {
        private readonly AppDbContext _context;

        public PagesController(AppDbContext context)
        {
            _context = context;
        }

        // Получить список всех страниц (для боковой панели)
        [HttpGet]
        public async Task<ActionResult<IEnumerable<Page>>> GetPages()
        {
            return await _context.Pages.ToListAsync();
        }

        // Получить одну страницу со всеми комментариями и версиями
        [HttpGet("{id}")]
        public async Task<ActionResult<Page>> GetPage(Guid id)
        {
            var page = await _context.Pages
                .Include(p => p.Comments) // Загружаем комментарии
                .Include(p => p.Versions.OrderByDescending(v => v.VersionNumber)) // Загружаем версии (сначала новые)
                .FirstOrDefaultAsync(p => p.Id == id);

            if (page == null) return NotFound();
            return page;
        }

        // Создать новую страницу
        [HttpPost]
        public async Task<ActionResult<Page>> PostPage(Page page)
        {
            page.Id = Guid.NewGuid();
            page.CreatedAt = DateTime.UtcNow;
            page.UpdatedAt = DateTime.UtcNow;
            page.Version = 1;

            _context.Pages.Add(page);
            await _context.SaveChangesAsync();

            return CreatedAtAction("GetPage", new { id = page.Id }, page);
        }

        // Обновить страницу (Автосохранение)
        [HttpPut("{id}")]
        public async Task<IActionResult> PutPage(Guid id, Page page)
        {
            if (id != page.Id) return BadRequest();

            var existingPage = await _context.Pages.FindAsync(id);
            if (existingPage == null) return NotFound();

            // МАШИНА ВРЕМЕНИ: Перед обновлением сохраняем текущее состояние как новую версию
            var pageVersion = new PageVersion
            {
                Id = Guid.NewGuid(),
                PageId = existingPage.Id,
                VersionNumber = existingPage.Version,
                Content = existingPage.Content,
                CreatedAt = DateTime.UtcNow,
                CreatedBy = page.UpdatedBy
            };
            _context.PageVersions.Add(pageVersion);

            // Обновляем данные основной страницы
            existingPage.Title = page.Title;
            existingPage.Content = page.Content;
            existingPage.UpdatedAt = DateTime.UtcNow;
            existingPage.UpdatedBy = page.UpdatedBy;
            existingPage.Version++; // Увеличиваем номер текущей версии

            await _context.SaveChangesAsync();
            return NoContent();
        }

        // Удалить страницу
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeletePage(Guid id)
        {
            var page = await _context.Pages.FindAsync(id);
            if (page == null) return NotFound();

            _context.Pages.Remove(page);
            await _context.SaveChangesAsync();
            return NoContent();
        }
        
        // ВОССТАНОВЛЕНИЕ: Вернуть страницу к определенной версии
        [HttpPost("{id}/restore/{versionId}")]
        public async Task<IActionResult> RestoreVersion(Guid id, Guid versionId)
        {
            var page = await _context.Pages.FindAsync(id);
            var version = await _context.PageVersions.FindAsync(versionId);
            
            if (page == null || version == null) return NotFound();
            
            page.Content = version.Content;
            page.UpdatedAt = DateTime.UtcNow;
            page.Version++;
            
            await _context.SaveChangesAsync();
            return Ok(page);
        }
    }
}