import React from 'react';
import { Search, Bell } from 'lucide-react';
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

export function TopNavigationBar() {
  return (
    <div className="h-16 border-b bg-white flex items-center justify-between px-6 sticky top-0 z-50 shadow-sm">
        
        {/* Global Search Bar */}
        <div className="flex-1 max-w-md relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-content-secondary" />
            <Input 
                placeholder="Search campaigns, subscribers, or workflows..." 
                className="pl-9 bg-slate-50 border-slate-200 focus-visible:ring-indigo-500" 
            />
            {/* Keyboard shortcut hint */}
            <div className="absolute right-3 top-1/2 -translate-y-1/2 flex gap-1">
                <kbd className="hidden sm:inline-flex h-5 items-center gap-1 rounded border bg-surface-secondary px-1.5 font-mono text-[10px] font-medium text-content-secondary">
                    <span className="text-xs">⌘</span>K
                </kbd>
            </div>
        </div>

        {/* Notifications & Profile */}
        <div className="flex items-center space-x-4">
            <Button variant="ghost" size="icon" className="relative text-slate-600 hover:text-indigo-600">
                <Bell className="h-5 w-5" />
                {/* Unread Badge indicator */}
                <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-red-500 ring-2 ring-white"></span>
            </Button>

            <div className="h-8 w-8 rounded-full bg-indigo-600 flex items-center justify-center text-white text-sm font-semibold shadow-sm cursor-pointer hover:bg-indigo-700 transition">
                JD
            </div>
        </div>
    </div>
  );
}
