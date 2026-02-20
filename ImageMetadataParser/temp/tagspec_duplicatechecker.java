        List<Taggable> allTags = new ArrayList<>();
 
        // Add each enum's contents
        allTags.addAll(Arrays.asList(TagIFD_Extension.values()));
        allTags.addAll(Arrays.asList(TagIFD_Exif.values()));
        allTags.addAll(Arrays.asList(TagIFD_GPS.values()));
        allTags.addAll(Arrays.asList(TagExif_Interop.values()));
        allTags.addAll(Arrays.asList(TagIFD_Private2.values()));
        allTags.addAll(Arrays.asList(TagIFD_RootSpec6.values()));

        // Convert to the array you wanted
        Taggable[] arr = allTags.toArray(new Taggable[0]);

        Map<DirectoryIdentifier, Map<Integer, Taggable>> TAG_REGISTRY = new HashMap<>();

        for (int i = 0; i < arr.length; i++)
        {
            Taggable tag = arr[i];
            DirectoryIdentifier dir = tag.getDirectoryType();
            int id = tag.getNumberID();

            if (!TAG_REGISTRY.containsKey(dir))
            {
                TAG_REGISTRY.put(dir, new HashMap<Integer, Taggable>());
            }

            Map<Integer, Taggable> map = TAG_REGISTRY.get(dir);

            if (map.containsKey(id))
            {
                Taggable existing = map.get(id);

                // Get the class names to identify the source Enums
                String existingEnumClass = existing.getClass().getSimpleName();
                String newEnumClass = tag.getClass().getSimpleName();

                System.err.printf("FATAL COLLISION in %s:%n", dir);
                System.err.printf("  ID: 0x%04X%n", id);
                System.err.printf("  Conflict: %s.%s  VS  %s.%s%n",
                        existingEnumClass, existing, newEnumClass, tag);
                System.err.println("--------------------------------------");
            }

            map.put(id, tag);
        }

        for (DirectoryIdentifier dir : TAG_REGISTRY.keySet())
        {
            Map<Integer, Taggable> map = TAG_REGISTRY.get(dir);

            for (Taggable tag : map.values())
            {
                System.out.printf("%s\t%s\n", dir, tag);
            }
        }