// Axios 封装：统一 baseURL、自动附带 JWT、401 时跳转登录
import axios from "axios";
import { useAuthStore } from "../stores/auth";

const TOKEN_KEY = "token";

const request = axios.create({
  baseURL: "/api",
  timeout: 10000
});

// 请求拦截：把 Pinia/localStorage 中的 token 写入 Authorization 头
request.interceptors.request.use((config) => {
  const store = useAuthStore();
  let token = store.token;
  if (!token) {
    token =
      localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || "";
  }
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截：未授权则登出并跳转登录页
request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      const store = useAuthStore();
      store.logout();
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

export default request;