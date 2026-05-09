import { defineStore } from "pinia";

const TOKEN_KEY = "token";
const USERNAME_KEY = "username";
const ROLE_KEY = "role";
const REMEMBER_KEY = "rememberMe";

function getStorage() {
  const rememberMe = localStorage.getItem(REMEMBER_KEY) === "1";
  return rememberMe ? localStorage : sessionStorage;
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || "",
    username: localStorage.getItem(USERNAME_KEY) || sessionStorage.getItem(USERNAME_KEY) || "",
    role: localStorage.getItem(ROLE_KEY) || sessionStorage.getItem(ROLE_KEY) || "",
    rememberMe: localStorage.getItem(REMEMBER_KEY) === "1"
  }),
  actions: {
    setAuth(payload, rememberMe) {
      this.rememberMe = !!rememberMe;
      localStorage.setItem(REMEMBER_KEY, this.rememberMe ? "1" : "0");
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USERNAME_KEY);
      localStorage.removeItem(ROLE_KEY);
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USERNAME_KEY);
      sessionStorage.removeItem(ROLE_KEY);

      const storage = this.rememberMe ? localStorage : sessionStorage;
      this.token = payload.token;
      this.username = payload.username;
      this.role = payload.role;
      storage.setItem(TOKEN_KEY, payload.token);
      storage.setItem(USERNAME_KEY, payload.username);
      storage.setItem(ROLE_KEY, payload.role);
    },
    logout() {
      this.token = "";
      this.username = "";
      this.role = "";
      this.rememberMe = false;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USERNAME_KEY);
      localStorage.removeItem(ROLE_KEY);
      localStorage.removeItem(REMEMBER_KEY);
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USERNAME_KEY);
      sessionStorage.removeItem(ROLE_KEY);
    },
    refreshFromStorage() {
      const primary = getStorage();
      const secondary = primary === localStorage ? sessionStorage : localStorage;
      this.token =
        primary.getItem(TOKEN_KEY) || secondary.getItem(TOKEN_KEY) || "";
      this.username =
        primary.getItem(USERNAME_KEY) || secondary.getItem(USERNAME_KEY) || "";
      this.role =
        primary.getItem(ROLE_KEY) || secondary.getItem(ROLE_KEY) || "";
    }
  }
});