package com.evirapo.diversityofcritters.client.guidebook.page;

import com.evirapo.diversityofcritters.client.guidebook.BookLayout;
import com.evirapo.diversityofcritters.client.guidebook.BookPage;
import net.minecraft.resources.ResourceLocation;

/** The book's first spread. */
public class HomePage extends BookPage {

    @Override
    public ResourceLocation background() {
        return BookLayout.HOME;
    }
}
