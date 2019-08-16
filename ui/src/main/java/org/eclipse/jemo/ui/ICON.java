/*
 ********************************************************************************
 * Copyright (c) 9th November 2018 Cloudreach Limited Europe
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 ********************************************************************************/
package org.eclipse.jemo.ui;

/**
 * this is an enumeration that maps all of the icons in the font-awesome collection
 *
 * @author christopher stura
 */
public enum ICON {
    angellist("fa-angellist"), area_chart("fa-area-chart"), at("fa-at"), bell_slash("fa-bell-slash"),
    bell_slash_o("fa-bell-slash-o"), bicycle("fa-bicycle"), binoculars("fa-binoculars"), birthday_cake("fa-birthday-cake"),
    bus("fa-bus"), calculator("fa-calculator"), cc("fa-cc"), cc_amex("fa-cc-amex"),
    cc_discover("fa-cc-discover"), cc_mastercard("fa-cc-mastercard"), cc_paypal("fa-cc-paypal"), cc_stripe("fa-cc-stripe"),
    cc_visa("fa-cc-visa"), copyright("fa-copyright"), eyedropper("fa-eyedropper"), futbol_o("fa-futbol-o"),
    google_wallet("fa-google-wallet"), ils("fa-ils"), ioxhost("fa-ioxhost"), lastfm("fa-lastfm"),
    lastfm_square("fa-lastfm-square"), line_chart("fa-line-chart"), meanpath("fa-meanpath"), newspaper_o("fa-newspaper-o"),
    paint_brush("fa-paint-brush"), paypal("fa-paypal"), pie_chart("fa-pie-chart"), plug("fa-plug"),
    slideshare("fa-slideshare"), toggle_off("fa-toggle-off"), toggle_on("fa-toggle-on"), trash("fa-trash"),
    tty("fa-tty"), twitch("fa-twitch"), wifi("fa-wifi"), yelp("fa-yelp"),
    ruble("fa-ruble"), pagelines("fa-pagelines"), stack_exchange("fa-stack-exchange"), arrow_circle_o_right("fa-arrow-circle-o-right"),
    arrow_circle_o_left("fa-arrow-circle-o-left"), caret_square_o_left("fa-caret-square-o-left"), dot_circle_o("fa-dot-circle-o"), wheelchair("fa-wheelchair"),
    vimeo_square("fa-vimeo-square"), turkish_lira("fa-turkish-lira"), plus_square_o("fa-plus-square-o"), car("fa-car"),
    bank("fa-bank"), behance("fa-behance"), behance_square("fa-behance-square"), bomb("fa-bomb"),
    building("fa-building"), cab("fa-cab"), child("fa-child"), circle_o_notch("fa-circle-o-notch"),
    circle_thin("fa-circle-thin"), codepen("fa-codepen"), cube("fa-cube"), cubes("fa-cubes"),
    database("fa-database"), delicious("fa-delicious"), deviantart("fa-deviantart"), digg("fa-digg"),
    drupal("fa-drupal"), empire("fa-empire"), envelope_square("fa-envelope-square"), fax("fa-fax"),
    file_archive_o("fa-file-archive-o"), file_audio_o("fa-file-audio-o"), file_code_o("fa-file-code-o"),
    file_excel_o("fa-file-excel-o"), file_image_o("fa-file-image-o"), file_pdf_o("fa-file-pdf-o"),
    file_powerpoint_o("fa-file-powerpoint-o"), file_video_o("fa-file-video-o"), file_word_o("fa-file-word-o"),
    git("fa-git"), git_square("fa-git-square"), google("fa-google"), graduation_cap("fa-graduation-cap"),
    hacker_news("fa-hacker-news"), header("fa-header"), history("fa-history"), joomla("fa-joomla"),
    jsfiddle("fa-jsfiddle"), language("fa-language"), life_ring("fa-life-ring"), openid("fa-openid"),
    paper_plane("fa-paper-plane"), paper_plane_o("fa-paper-plane-o"), paragraph("fa-paragraph"), paw("fa-paw"),
    pied_piper("fa-pied-piper"), pied_piper_alt("fa-pied-piper-alt"), qq("fa-qq"), rebel("fa-rebel"),
    recycle("fa-recycle"), reddit("fa-reddit"), reddit_square("fa-reddit-square"), share_alt("fa-share-alt"),
    share_alt_square("fa-share-alt-square"), slack("fa-slack"), sliders("fa-sliders"), soundcloud("fa-soundcloud"),
    space_shuttle("fa-space-shuttle"), spoon("fa-spoon"), spotify("fa-spotify"), steam("fa-steam"),
    steam_square("fa-steam-square"), stumbleupon("fa-stumbleupon"), stumbleupon_circle("fa-stumbleupon-circle"), tencent_weibo("fa-tencent-weibo"),
    tree("fa-tree"), university("fa-university"), vine("fa-vine"), wechat("fa-wechat"),
    wordpress("fa-wordpress"), yahoo("fa-yahoo"), user("fa-user"), users("fa-users"), group("fa-users"),
    filter("fa-filter"), cog("fa-cog"), cogs("fa-cogs"), workflow("fa-cogs"), file("fa-file-o"), thlist("fa-th-list");
    //there are still many icons which should be mapped in this file which are not.

    String className = null;

    ICON(String className) {
        this.className = className;
    }

    @Override
    public String toString() {
        return this.className;
    }
}
