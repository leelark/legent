import os
import glob

# Find all application.yml in services
yaml_files = glob.glob('services/*/src/main/resources/application.yml')

for file_path in yaml_files:
    with open(file_path, 'r') as f:
        content = f.read()
    
    # 1. Add spring.jpa.open-in-view: false
    if 'open-in-view: false' not in content:
        # find where jpa: is, and insert open-in-view: false under it
        if '\n  jpa:\n' in content:
            content = content.replace('\n  jpa:\n', '\n  jpa:\n    open-in-view: false\n')
        elif '\nspring:\n' in content:
            content = content.replace('\nspring:\n', '\nspring:\n  jpa:\n    open-in-view: false\n')
            
    # 2. Add autoconfigure exclude
    exclude_str = """
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"""
      
    if 'autoconfigure:' not in content:
        if '\nspring:\n' in content:
            content = content.replace('\nspring:\n', '\nspring:' + exclude_str + '\n')
            
    with open(file_path, 'w') as f:
        f.write(content)
    print(f"Updated {file_path}")
