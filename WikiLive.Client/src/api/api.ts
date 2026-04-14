import axios from 'axios';

// Настройка базового пути для запросов
const api = axios.create({
  baseURL: '/api'
});

// Функции для работы со страницами
export const pagesApi = {
  // Получить все страницы для сайдбара
  getAll: async () => {
    const response = await api.get('/Pages');
    return response.data;
  },
  
  // Получить одну страницу (вместе с контентом и комментариями)
  getById: async (id: string) => {
    const response = await api.get(`/Pages/${id}`);
    return response.data;
  },
  
  // Создать новую страницу
  create: async (title: string) => {
    const response = await api.post('/Pages', { title, content: {} });
    return response.data;
  },
  
  // Обновить страницу (автосохранение)
  update: async (id: string, data: any) => {
    return await api.put(`/Pages/${id}`, data);
  },
  
  // Восстановить версию
  restoreVersion: async (pageId: string, versionId: string) => {
    const response = await api.post(`/Pages/${pageId}/restore/${versionId}`);
    return response.data;
  }
};

// Функции для работы с комментариями
export const commentsApi = {
  // Добавить новый комментарий
  create: async (comment: { pageId: string, text: string, selectedText?: string, parentId?: string }) => {
    const response = await api.post('/Comments', comment);
    return response.data;
  },
  
  // Поставить лайк
  like: async (id: string) => {
    const response = await api.post(`/Comments/${id}/like`);
    return response.data;
  },
  
  // Пометить как решенное
  resolve: async (id: string) => {
    return await api.put(`/Comments/${id}/resolve`);
  },
  
  // Удалить
  delete: async (id: string) => {
    return await api.delete(`/Comments/${id}`);
  }
};

export default api;
