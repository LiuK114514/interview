const BASE_URL = '/api';

async function request(url, options = {}) {
  const config = {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  };

  if (config.body && typeof config.body === 'object' && !(config.body instanceof FormData)) {
    config.body = JSON.stringify(config.body);
  }

  const res = await fetch(`${BASE_URL}${url}`, config);
  const data = await res.json();

  if (!res.ok || data.code !== 0) {
    throw new Error(data.message || `请求失败 (${res.status})`);
  }
  return data.data;
}

export const chat = {
  send: (body) => request('/chat', { method: 'POST', body }),
  analyze: (body) => request('/chat/analyze', { method: 'POST', body }),
};

export const interview = {
  start: (body) => request('/interview/start', { method: 'POST', body }),
  answer: (body) => request('/interview/answer', { method: 'POST', body }),
  next: (body) => request('/interview/next', { method: 'POST', body }),
  report: (body) => request('/interview/report', { method: 'POST', body }),
};

export const resumes = {
  upload: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${BASE_URL}/resumes/upload`, { method: 'POST', body: formData });
    const data = await res.json();
    if (!res.ok || data.code !== 0) throw new Error(data.message || '上传失败');
    return data.data;
  },
  list: () => request('/resumes'),
  detail: (id) => request(`/resumes/${id}/detail`),
  delete: (id) => request(`/resumes/${id}`, { method: 'DELETE' }),
  reanalyze: (id) => request(`/resumes/${id}/reanalyze`, { method: 'POST' }),
};

export const dlq = {
  list: () => request('/dlq'),
  retry: (recordId) => request(`/dlq/${encodeURIComponent(recordId)}/retry`, { method: 'POST' }),
  retryAll: () => request('/dlq/retry-all', { method: 'POST' }),
  delete: (recordId) => request(`/dlq/${encodeURIComponent(recordId)}`, { method: 'DELETE' }),
  clearAll: () => request('/dlq', { method: 'DELETE' }),
};
