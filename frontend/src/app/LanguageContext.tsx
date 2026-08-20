import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

type Language = 'ko' | 'en';
type LanguageContextValue = { language: Language; setLanguage: (language: Language) => void; t: (ko: string, en: string) => string; };
const LanguageContext = createContext<LanguageContextValue>({ language: 'ko', setLanguage: () => undefined, t: (ko) => ko });

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(() => localStorage.getItem('language') === 'en' ? 'en' : 'ko');
  useEffect(() => { localStorage.setItem('language', language); document.documentElement.lang = language; }, [language]);
  const value = useMemo(() => ({ language, setLanguage, t: (ko: string, en: string) => language === 'ko' ? ko : en }), [language]);
  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() { return useContext(LanguageContext); }
