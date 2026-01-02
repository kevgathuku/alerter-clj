#!/bin/bash
# Migrate alert files from old structure to new structure
# Old: content/{rule-id}/YYYY-MM-DD/{timestamp}.edn
# New: content/YYYY-MM-DD/{timestamp}.edn
#
# Usage:
#   ./scripts/migrate-alert-structure.sh          # Perform migration
#   ./scripts/migrate-alert-structure.sh --dry-run # Show what would be done

set -e

DRY_RUN=false
if [ "$1" = "--dry-run" ]; then
  DRY_RUN=true
  echo "DRY RUN MODE - no files will be moved"
  echo ""
fi

CONTENT_DIR="content"

# Check if content directory exists
if [ ! -d "$CONTENT_DIR" ]; then
  echo "Content directory not found. Nothing to migrate."
  exit 0
fi

# Find all rule-id directories (directories that contain date subdirectories)
echo "Scanning for old structure files..."

migrated_count=0
skipped_count=0

# Find all .edn files in the old structure
find "$CONTENT_DIR" -type f -name "*.edn" | while read -r old_file; do
  # Parse the path: content/{rule-id}/{date}/{timestamp}.edn
  # Extract components
  relative_path="${old_file#$CONTENT_DIR/}"

  # Check if it matches old structure (has at least 3 parts)
  IFS='/' read -ra parts <<< "$relative_path"

  if [ ${#parts[@]} -eq 3 ]; then
    rule_id="${parts[0]}"
    date_str="${parts[1]}"
    filename="${parts[2]}"

    # Check if date_str looks like a date (YYYY-MM-DD)
    if [[ "$date_str" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
      # This is old structure, migrate it
      timestamp="${filename%.edn}"
      new_dir="$CONTENT_DIR/$date_str"
      new_file="$new_dir/${timestamp}-${rule_id}.edn"

      # Check if file already exists
      if [ -f "$new_file" ]; then
        echo "⚠ Skipping $old_file (destination already exists: $new_file)"
        ((skipped_count++))
      else
        echo "Moving $old_file -> $new_file"
        if [ "$DRY_RUN" = false ]; then
          mkdir -p "$new_dir"
          mv "$old_file" "$new_file"
        fi
        ((migrated_count++))
      fi
    fi
  fi
done

echo ""
echo "Migration complete!"
echo "Files migrated: $migrated_count"
echo "Files skipped: $skipped_count"

# Clean up empty rule-id directories
if [ "$DRY_RUN" = false ]; then
  echo ""
  echo "Cleaning up empty directories..."
  find "$CONTENT_DIR" -mindepth 1 -type d -empty -delete 2>/dev/null || true
fi

echo "Done!"
