using Microsoft.EntityFrameworkCore;
using WikiLive.Api.Models;

namespace WikiLive.Api.Data
{
    // Класс контекста базы данных - "мост" между кодом C# и PostgreSQL
    public class AppDbContext : DbContext
    {
        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

        // Регистрация таблиц в базе данных
        public DbSet<Page> Pages { get; set; }
        public DbSet<PageVersion> PageVersions { get; set; }
        public DbSet<Comment> Comments { get; set; }
        public DbSet<Backlink> Backlinks { get; set; }

        // Настройка правил создания таблиц
        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            // Настройка связи страницы и комментариев:
            // При удалении страницы все её комментарии удалятся автоматически (Cascade)
            modelBuilder.Entity<Page>()
                .HasMany(p => p.Comments)
                .WithOne(c => c.Page)
                .HasForeignKey(c => c.PageId)
                .OnDelete(DeleteBehavior.Cascade);

            // Настройка связи страницы и её версий
            modelBuilder.Entity<Page>()
                .HasMany(p => p.Versions)
                .WithOne(v => v.Page)
                .HasForeignKey(v => v.PageId)
                .OnDelete(DeleteBehavior.Cascade);
            
            // Указываем PostgreSQL использовать специальный тип jsonb для хранения контента
            // Это позволяет быстро работать с большими JSON-документами TipTap
            modelBuilder.Entity<Page>()
                .Property(b => b.Content)
                .HasColumnType("jsonb");

            modelBuilder.Entity<PageVersion>()
                .Property(b => b.Content)
                .HasColumnType("jsonb");
        }
    }
}