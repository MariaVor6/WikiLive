using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Text.Json;

namespace WikiLive.Api.Models
{
    // Основная сущность страницы Вики
    public class Page
    {
        // Уникальный идентификатор страницы (GUID)
        public Guid Id { get; set; }

        // Заголовок страницы (обязательное поле)
        [Required]
        public string Title { get; set; } = string.Empty;

        // Содержимое страницы в формате JSON (от редактора TipTap)
        // Тип jsonb в PostgreSQL позволяет эффективно хранить и искать в JSON
        public JsonDocument? Content { get; set; }

        // Идентификатор пространства в MWS Tables, к которому привязана страница
        public string? SpaceId { get; set; }

        // Дата и время создания (по умолчанию - текущее время UTC)
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        // Дата и время последнего обновления
        public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

        // Кто создал страницу (ID пользователя)
        public string? CreatedBy { get; set; }

        // Кто последний редактировал страницу
        public string? UpdatedBy { get; set; }

        // Текущий номер версии страницы (увеличивается при каждом сохранении)
        public int Version { get; set; } = 1;

        // Связь "один-ко-многим": у одной страницы может быть много комментариев
        public ICollection<Comment> Comments { get; set; } = new List<Comment>();

        // Связь "один-ко-многим": история всех версий этой страницы
        public ICollection<PageVersion> Versions { get; set; } = new List<PageVersion>();
    }

    // Сущность для хранения истории версий (Машина времени)
    public class PageVersion
    {
        public Guid Id { get; set; }
        public Guid PageId { get; set; } // Ссылка на основную страницу
        public int VersionNumber { get; set; } // Номер этой версии
        public JsonDocument? Content { get; set; } // Копия контента на момент сохранения
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
        public string? CreatedBy { get; set; }
        public string? Comment { get; set; } // Опциональный комментарий к версии

        // Навигационное свойство для связи в коде
        public Page? Page { get; set; }
    }

    // Сущность комментария
    public class Comment
    {
        public Guid Id { get; set; }
        public Guid PageId { get; set; }
        
        // Текст, который был выделен пользователем перед комментированием
        public string? SelectedText { get; set; }

        // Текст самого комментария
        [Required]
        public string Text { get; set; } = string.Empty;

        // Статус: решен вопрос или еще открыт
        public bool Resolved { get; set; } = false;
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
        public string? CreatedBy { get; set; }

        // ID родительского комментария (для создания цепочек ответов)
        public Guid? ParentId { get; set; }

        // Количество лайков под комментарием
        public int Likes { get; set; } = 0;

        public Page? Page { get; set; }
    }

    // Сущность для "Обратных ссылок" (Backlinks)
    // Позволяет узнать, какие страницы ссылаются на текущую
    public class Backlink
    {
        public Guid Id { get; set; }
        public Guid FromPageId { get; set; } // Откуда пришла ссылка
        public Guid ToPageId { get; set; }   // На какую страницу указывает
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    }
}