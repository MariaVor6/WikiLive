using Microsoft.AspNetCore.Mvc;
using WikiLive.Api.Data;
using WikiLive.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace WikiLive.Api.Controllers
{
    // Контроллер для работы с комментариями
    [Route("api/[controller]")]
    [ApiController]
    public class CommentsController : ControllerBase
    {
        private readonly AppDbContext _context;

        public CommentsController(AppDbContext context)
        {
            _context = context;
        }

        // Добавить новый комментарий (или ответ на комментарий)
        [HttpPost]
        public async Task<ActionResult<Comment>> PostComment(Comment comment)
        {
            comment.Id = Guid.NewGuid();
            comment.CreatedAt = DateTime.UtcNow;
            
            _context.Comments.Add(comment);
            await _context.SaveChangesAsync();

            return Ok(comment);
        }

        // Поставить лайк комментарию
        [HttpPost("{id}/like")]
        public async Task<IActionResult> LikeComment(Guid id)
        {
            var comment = await _context.Comments.FindAsync(id);
            if (comment == null) return NotFound();

            comment.Likes++;
            await _context.SaveChangesAsync();
            return Ok(new { likes = comment.Likes });
        }

        // Отметить ветку комментариев как решенную (архивировать)
        [HttpPut("{id}/resolve")]
        public async Task<IActionResult> ResolveComment(Guid id)
        {
            var comment = await _context.Comments.FindAsync(id);
            if (comment == null) return NotFound();

            comment.Resolved = true;
            await _context.SaveChangesAsync();
            return NoContent();
        }

        // Удалить комментарий
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeleteComment(Guid id)
        {
            var comment = await _context.Comments.FindAsync(id);
            if (comment == null) return NotFound();

            _context.Comments.Remove(comment);
            await _context.SaveChangesAsync();
            return NoContent();
        }
    }
}